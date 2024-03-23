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
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import com.kenvix.sensorcollector.hardware.vendor.SensorData
import com.kenvix.sensorcollector.hardware.vendor.SensorDataParser
import com.kenvix.sensorcollector.utils.RecordWriter
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import kotlin.coroutines.resume


object UsbSerial : AutoCloseable,
    CoroutineScope by CoroutineScope(CoroutineName("UsbSerial")) {
    private lateinit var usbManager: UsbManager
    private var permissionContinuation: CancellableContinuation<Boolean>? = null
    private val opMutex = Mutex()
    val openedSerialDevices: MutableMap<UsbDevice, Pair<UsbSerialDevice, Deferred<Unit>>> =
        mutableMapOf()

    private const val ACTION_USB_PERMISSION = "com.kenvix.sensorcollector.USB_PERMISSION"

    val selectedDevices = HashSet<UsbDevice>()

    fun init(sysContext: Context) {
        usbManager = sysContext.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                permissionContinuation!!.resume(
                    intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )
                )
            }
        }
    }

    fun startReceiving(
        device: UsbDevice,
        dataParser: SensorDataParser,
        delimiter: Byte,
        onReceived: (UsbDevice, UsbSerialDevice, SensorData) -> Unit
    ): Deferred<Unit> {
        val usbConnection: UsbDeviceConnection = usbManager.openDevice(device)
        var job: Deferred<Unit>

        val serial: UsbSerialDevice =
            UsbSerialDevice.createUsbSerialDevice(device, usbConnection).apply {
                syncOpen()
                dataParser.prepareSerialDevice(device, this)

                job = async(Dispatchers.IO + coroutineContext) {
                    val inputStream = DataInputStream(BufferedInputStream(inputStream))
                    try {
                        while (isActive) {
                            val header = inputStream.readByte()
                            if (header == dataParser.packetHeader) {
                                dataParser.onDataInput(device, this@apply, inputStream, onReceived)
                            }
                        }
                    } catch (ignored: EOFException) {
                        Log.i("UsbSerial", "EOF of device ${device.deviceName}")
                    }
                }
            }

        openedSerialDevices[device] = Pair(serial, job)
        return job
    }

    suspend fun startReceivingAllAndWait(
        dataParser: SensorDataParser,
        writer: RecordWriter,
        delimiter: Byte,
        onReceived: (UsbDevice, UsbSerialDevice, SensorData) -> Unit
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

        jobs.awaitAll()
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    suspend fun requestPermission(uiContext: Context, device: UsbDevice): Boolean {
        opMutex.withLock {
            try {
                return suspendCancellableCoroutine { continuation ->
                    val permissionIntent =
                        PendingIntent.getBroadcast(uiContext, 0, Intent(ACTION_USB_PERMISSION), 0)
                    val filter = IntentFilter(ACTION_USB_PERMISSION)
                    uiContext.registerReceiver(usbReceiver, filter)
                    permissionContinuation = continuation
                    usbManager.requestPermission(device, permissionIntent)
                    continuation.invokeOnCancellation {
                        uiContext.unregisterReceiver(usbReceiver)
                    }
                }
            } finally {
                uiContext.unregisterReceiver(usbReceiver)
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
            entry.value.second.cancel()
            entry.value.first.close()
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
