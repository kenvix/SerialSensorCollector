//--------------------------------------------------
// Class WitHardwareDataParser
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.hardware.vendor

import android.hardware.usb.UsbDevice
import com.felhr.usbserial.UsbSerialDevice
import com.google.common.io.LittleEndianDataInputStream
import com.kenvix.sensorcollector.hardware.vendor.SensorData.Companion.GRAVITY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.DataInputStream


class WitHardwareDataParser : SensorDataParser {
    override val packetHeader: Byte = 0x55

    override fun prepareSerialDevice(device: UsbDevice, serial: UsbSerialDevice) {
        serial.setBaudRate(115200)
    }

    override suspend fun onDataInput(
        device: UsbDevice,
        serial: UsbSerialDevice,
        data: DataInputStream,
        onReceived: suspend (UsbDevice, UsbSerialDevice, SensorData) -> Unit
    ) {
        var sensorData: SensorData? = null

        runInterruptible(Dispatchers.IO) {
            val flag = data.readByte()
            val dataLE = LittleEndianDataInputStream(data)

            val accX = dataLE.readShort() / 32768.0 * 16.0 * GRAVITY
            val accY = dataLE.readShort() / 32768.0 * 16.0 * GRAVITY
            val accZ = dataLE.readShort() / 32768.0 * 16.0 * GRAVITY

            val gyroX = dataLE.readShort() / 32768.0 * 2000.0
            val gyroY = dataLE.readShort() / 32768.0 * 2000.0
            val gyroZ = dataLE.readShort() / 32768.0 * 2000.0

            val angleX = dataLE.readShort() / 32768.0 * 180.0
            val angleY = dataLE.readShort() / 32768.0 * 180.0
            val angleZ = dataLE.readShort() / 32768.0 * 180.0

            sensorData = SensorData(
                accX, accY, accZ,
                gyroX, gyroY, gyroZ,
                angleX, angleY, angleZ
            )
        }

        if (sensorData != null)
            onReceived(device, serial, sensorData!!)
    }
}
