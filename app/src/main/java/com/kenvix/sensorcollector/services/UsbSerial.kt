//--------------------------------------------------
// Class UsbSerial
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import com.kenvix.sensorcollector.hardware.vendor.SensorData
import com.kenvix.sensorcollector.hardware.vendor.SensorDataParser
import com.kenvix.sensorcollector.utils.RecordWriter
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume


object UsbSerial : AutoCloseable,
    CoroutineScope by CoroutineScope(CoroutineName("UsbSerial")) {
    private lateinit var usbManager: UsbManager
    private var permissionContinuation: CancellableContinuation<Boolean>? = null
    val opMutex = Mutex()
    val openedSerialDevices: MutableMap<UsbDevice, OpenedSerialDevice> =
        mutableMapOf()

    data class OpenedSerialDevice(val serial: UsbSerialDevice, val connection: UsbDeviceConnection, val job: Job)

    private const val ACTION_USB_PERMISSION = "com.kenvix.sensorcollector.USB_PERMISSION"

    val selectedDevices = HashSet<UsbDevice>()

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun init(sysContext: Context) {
        usbManager = sysContext.getSystemService(Context.USB_SERVICE) as UsbManager

        try {
            val filter = IntentFilter(ACTION_USB_PERMISSION)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                sysContext.registerReceiver(usbReceiver, filter)
            } else {
                sysContext.registerReceiver(
                    usbReceiver, filter,
                    Context.RECEIVER_EXPORTED
                )
            }
        } catch (e: IllegalStateException) {
            Log.w("UsbSerial", "Failed to register USB receiver", e)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerUsbReceiver(uiContext: Context) {

    }

    val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    permissionContinuation!!.resume(granted)
                }
            }
        }
    }

    fun startReceiving(
        device: UsbDevice,
        dataParser: SensorDataParser,
        delimiter: Byte,
        onReceived: suspend (UsbDevice, UsbSerialDevice, SensorData) -> Unit
    ): Job {
        Log.d("UsbSerial", "Starting serial for ${device.deviceName} [1/6]: Opening USB connection")
        val usbConnection: UsbDeviceConnection = usbManager.openDevice(device)
        var job: Job

        Log.d("UsbSerial", "Starting serial for ${device.deviceName} [2/6]: Create serial device")
        val serial: UsbSerialDevice =
            UsbSerialDevice.createUsbSerialDevice(device, usbConnection).apply {
                Log.d("UsbSerial", "Starting serial for ${device.deviceName} [3/6]: Open serial device")

                val future = CompletableFuture.runAsync {
                    if (!syncOpen()) {
                        Log.e("UsbSerial", "Failed to open serial device ${device.deviceName}")
                        throw IllegalStateException("Failed to open serial device ${device.deviceName}")
                    }
                }

                try {
                    future.get(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    Log.e("UsbSerial", "Timed out to open serial device ${device.deviceName} syncOpen() failed", e)
                    throw IllegalStateException("Timed out to open open serial device ${device.deviceName} syncOpen() failed: $e", e)
                }

                Log.d("UsbSerial", "Starting serial for ${device.deviceName} [4/6]: Prepare serial device")
                dataParser.prepareSerialDevice(device, this)

                Log.d("UsbSerial", "Starting serial for ${device.deviceName} [5/6]: Start data receive job")
                job = launch(Dispatchers.IO + coroutineContext) {
                    Log.d("UsbSerial", "Starting serial for ${device.deviceName} [6/6]: Start data stream")
                    val inputStream = DataInputStream(BufferedInputStream(inputStream))
                    try {
                        while (isActive) {
                            val header = inputStream.readByte()
                            if (header == dataParser.packetHeader) {
                                dataParser.onDataInput(device, this@apply, inputStream, onReceived)
                            }
                        }

                        Log.d("UsbSerial", "Receiver finished: ${device.deviceName}")
                    } catch (_: EOFException) {
                        Log.i("UsbSerial", "EOF of device ${device.deviceName}")
                    } catch (e: CancellationException) {
                        Log.i("UsbSerial", "Cancellation of Worker of device ${device.deviceName}")
                        throw e
                    }
                }
            }

        openedSerialDevices[device] = OpenedSerialDevice(serial, connection = usbConnection, job)
        return job
    }

    suspend fun startReceivingAllAndWait(
        dataParser: SensorDataParser,
        writer: RecordWriter,
        delimiter: Byte,
        onReceived: suspend (UsbDevice, UsbSerialDevice, SensorData) -> Unit
    ) {
        val jobs = opMutex.withLock {
            selectedDevices.map {
                withContext(Dispatchers.IO) {
                    startReceiving(
                        it,
                        dataParser,
                        delimiter = dataParser.packetHeader
                    ) { device, serial, data ->
                        writer.onSensorDataReceived(device, serial, data)
                    }
                }
            }
        }

        jobs.joinAll()
    }

    fun getAvailableUsbSerialDevices(): List<UsbDevice> {
        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList

        val usbSerialDevices = mutableListOf<UsbDevice>()

        for (device in deviceList.values) {
            // Check if the device is a USB serial device. The class of USB serial devices is defined as 0x02.
            if (device.deviceClass == UsbConstants.USB_CLASS_CDC_DATA || device.deviceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                usbSerialDevices.add(device)
            }
        }

        return usbSerialDevices
    }

    @SuppressLint("MutableImplicitPendingIntent")
    suspend fun requestPermission(uiContext: Context, device: UsbDevice): Boolean {
        return opMutex.withLock {
            suspendCancellableCoroutine { continuation ->
                val permissionIntent =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        PendingIntent.getBroadcast(
                            uiContext, 0, Intent(ACTION_USB_PERMISSION),
                            PendingIntent.FLAG_MUTABLE
                        )
                    } else {
                        PendingIntent.getBroadcast(
                            uiContext, 0, Intent(ACTION_USB_PERMISSION),
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
                        )
                    }
                permissionContinuation = continuation
                usbManager.requestPermission(device, permissionIntent)
            }
        }
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    fun stopAllSerialUnsafe() {
        //close and remove from openedSerialDevices
        val iterator = openedSerialDevices.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val (device, conn, job) = entry.value
            Log.d("UsbSerial", "Stopping serial for ${entry.key.deviceName} [1/4]: Canceling job [${job.key}")
            job.cancel()
            Log.d("UsbSerial", "Stopping serial for ${entry.key.deviceName} [2/4]: Closing serial device")
            device.inputStream.close()
            device.outputStream.close()
            device.syncClose()
            Log.d("UsbSerial", "Stopping serial for ${entry.key.deviceName} [3/4]: Closing USB connection")
            conn.close()
            iterator.remove()
        }
    }

    suspend fun stopAllSerial() {
        opMutex.withLock {
            stopAllSerialUnsafe()
        }
    }

    override fun close() {
        stopAllSerialUnsafe()
    }
}
