//--------------------------------------------------
// Interface SensorDataParser
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.hardware.vendor

import android.hardware.usb.UsbDevice
import com.felhr.usbserial.UsbSerialDevice
import java.io.DataInputStream
import java.io.Serializable

interface SensorDataParser: Serializable {
    fun prepareSerialDevice(device: UsbDevice, serial: UsbSerialDevice)
    suspend fun onDataInput(
        device: UsbDevice,
        serial: UsbSerialDevice,
        data: DataInputStream,
        onReceived: suspend (UsbDevice, UsbSerialDevice, SensorData) -> Unit
    )

    val packetHeader: Byte
}
