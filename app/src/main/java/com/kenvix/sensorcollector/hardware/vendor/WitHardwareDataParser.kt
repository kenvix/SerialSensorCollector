//--------------------------------------------------
// Class WitHardwareDataParser
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.hardware.vendor

import android.hardware.usb.UsbDevice
import com.felhr.usbserial.UsbSerialDevice
import com.kenvix.sensorcollector.hardware.vendor.SensorData.Companion.GRAVITY
import java.io.DataInputStream


class WitHardwareDataParser : SensorDataParser {
    override val packetHeader: Byte = 0x55

    override fun prepareSerialDevice(device: UsbDevice, serial: UsbSerialDevice) {
        serial.setBaudRate(115200)
    }

    override fun onDataInput(
        device: UsbDevice,
        serial: UsbSerialDevice,
        data: DataInputStream,
        onReceived: (UsbDevice, UsbSerialDevice, SensorData) -> Unit
    ) {
        val flag = data.readByte()
        val accX =
            ((data.readByte().toInt()) + (data.readByte().toInt() shl 8)) / 32768.0 * 16.0 * GRAVITY
        val accY =
            ((data.readByte().toInt()) + (data.readByte().toInt() shl 8)) / 32768.0 * 16.0 * GRAVITY
        val accZ =
            ((data.readByte().toInt()) + (data.readByte().toInt() shl 8)) / 32768.0 * 16.0 * GRAVITY

        val gyroX =
            ((data.readByte().toInt()) + (data.readByte().toInt() shl 8)) / 32768.0 * 2000.0
        val gyroY =
            ((data.readByte().toInt()) + (data.readByte().toInt() shl 8)) / 32768.0 * 2000.0
        val gyroZ =
            ((data.readByte().toInt()) + (data.readByte().toInt() shl 8)) / 32768.0 * 2000.0

        val angleX =
            ((data.readByte().toInt()) + (data.readByte().toInt() shl 8)) / 32768.0 * 180.0
        val angleY =
            ((data.readByte().toInt()) + (data.readByte().toInt() shl 8)) / 32768.0 * 180.0
        val angleZ =
            ((data.readByte().toInt()) + (data.readByte().toInt() shl 8)) / 32768.0 * 180.0

        val sensorData = SensorData(
            accX, accY, accZ,
            gyroX, gyroY, gyroZ,
            angleX, angleY, angleZ
        )

        onReceived(device, serial, sensorData)
    }
}
