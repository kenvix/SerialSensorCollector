//--------------------------------------------------
// Class WitHardwareDataParser
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.hardware.vendor

import com.kenvix.sensorcollector.hardware.vendor.SensorData.Companion.GRAVITY


class WitHardwareDataParser: SensorDataParser {
    override val packetHeader: ByteArray = byteArrayOf(0x55)

    override fun onDataInput(data: ByteArray, onReceived: (SensorData) -> Unit) {
        val header = data[0]
        if (header != packetHeader[0]) return
        val flag = data[1]

        val offset = 2

        val accX = ((data[offset + 0].toInt()) + (data[offset + 1].toInt() shl 8)) / 32768.0 * 16.0 * GRAVITY
        val accY = ((data[offset + 2].toInt()) + (data[offset + 3].toInt() shl 8)) / 32768.0 * 16.0 * GRAVITY
        val accZ = ((data[offset + 4].toInt()) + (data[offset + 5].toInt() shl 8)) / 32768.0 * 16.0 * GRAVITY

        val gyroX = ((data[offset + 6].toInt()) + (data[offset + 7].toInt() shl 8)) / 32768.0 * 2000.0
        val gyroY = ((data[offset + 8].toInt()) + (data[offset + 9].toInt() shl 8)) / 32768.0 * 2000.0
        val gyroZ = ((data[offset + 10].toInt()) + (data[offset + 11].toInt() shl 8)) / 32768.0 * 2000.0

        val angleX = ((data[offset + 12].toInt()) + (data[offset + 13].toInt() shl 8)) / 32768.0 * 180.0
        val angleY = ((data[offset + 14].toInt()) + (data[offset + 15].toInt() shl 8)) / 32768.0 * 180.0
        val angleZ = ((data[offset + 16].toInt()) + (data[offset + 17].toInt() shl 8)) / 32768.0 * 180.0

        val sensorData = SensorData(
            accX, accY, accZ,
            gyroX, gyroY, gyroZ,
            angleX, angleY, angleZ
        )

        onReceived(sensorData)
    }
}
