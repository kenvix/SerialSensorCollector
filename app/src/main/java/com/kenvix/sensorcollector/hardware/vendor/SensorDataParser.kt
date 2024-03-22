//--------------------------------------------------
// Interface SensorDataParser
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.hardware.vendor

import android.hardware.usb.UsbDevice
import com.felhr.usbserial.UsbSerialDevice
import java.io.DataInputStream

interface SensorDataParser {
    fun prepareSerialDevice(device: UsbDevice, serial: UsbSerialDevice)
    fun onDataInput(
        device: UsbDevice,
        serial: UsbSerialDevice,
        data: DataInputStream,
        onReceived: (UsbDevice, UsbSerialDevice, SensorData) -> Unit
    )

    val packetHeader: Byte
}
