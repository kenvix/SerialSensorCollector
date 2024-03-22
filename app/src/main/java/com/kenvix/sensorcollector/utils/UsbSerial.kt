//--------------------------------------------------
// Class UsbSerial
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

class UsbSerial(private val context: Context) {
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var permissionContinuation: CancellableContinuation<Boolean>? = null
    private val opMutex = Mutex()
    companion object Utils {
        private const val ACTION_USB_PERMISSION = "com.kenvix.sensorcollector.USB_PERMISSION"
    }

    val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                permissionContinuation!!.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
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
    suspend fun requestPermission(device: UsbDevice): Boolean {
        opMutex.withLock {
            try {
                return suspendCancellableCoroutine { continuation ->
                    val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)
                    val filter = IntentFilter(ACTION_USB_PERMISSION)
                    context.registerReceiver(usbReceiver, filter)
                    permissionContinuation = continuation
                    usbManager.requestPermission(device, permissionIntent)
                    continuation.invokeOnCancellation {
                        context.unregisterReceiver(usbReceiver)
                    }
                }
            } finally {
                context.unregisterReceiver(usbReceiver)
            }
        }
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }
}
