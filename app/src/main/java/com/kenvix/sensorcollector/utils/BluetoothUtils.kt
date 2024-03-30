package com.kenvix.sensorcollector.utils

import android.bluetooth.le.ScanCallback
import org.apache.poi.util.LittleEndianByteArrayInputStream
import java.io.ByteArrayInputStream

fun getScanFailureMessage(errorCode: Int): String {
    return when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan failed: already started"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Scan failed: app registration failed"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Scan failed: feature unsupported"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Scan failed: internal error"
        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scan failed: scanning too frequently"
        else -> "Unknown scan failure error: $errorCode"
    }
}

fun ByteArray.toHexString(): String {
    return joinToString("") { byte ->
        "%02x".format(byte)
    }
}

fun parserServiceData(uuid: String, data: ByteArray): Any? {
    return when (uuid) {
        // PVVX Custom Thermometer Format
        // reference: https://github.com/pvvx/ATC_MiThermometer
        "0000181a-0000-1000-8000-00805f9b34fb" -> {
            LittleEndianByteArrayInputStream(data).use {
                it.skip(6) // mac address. unused
                val mTemperature = it.readShort()
                val mHumidity = it.readUShort()
                val mBatteryMv = it.readUShort()
                val mBatteryLevel = it.readUByte()
                val counter = it.readUByte()
                val flags = it.readUByte()
                ThermometerData(
                    temperature = mTemperature / 100.0F,
                    humidity = mHumidity / 100.0F,
                    batteryLevel = mBatteryLevel.toFloat(),
                    batteryMV = mBatteryMv.toFloat()
                )
            }
        }
        "0000fcd2-0000-1000-8000-00805f9b34fb" -> { // todo
            return null
            val temperature = ((data[1].toInt() and 0xff) shl 8) or (data[0].toInt() and 0xff)
            val humidity = ((data[3].toInt() and 0xff) shl 8) or (data[2].toInt() and 0xff)
            val pressure = ((data[7].toInt() and 0xff) shl 24) or ((data[6].toInt() and 0xff) shl 16) or ((data[5].toInt() and 0xff) shl 8) or (data[4].toInt() and 0xff)
            val battery = ((data[9].toInt() and 0xff) shl 8) or (data[8].toInt() and 0xff)
            return ThermometerData(
                temperature = temperature / 100.0F,
                humidity = humidity / 100.0F,
                pressure = pressure / 100.0F,
                batteryMV = battery / 100.0F
            )
        }
        else -> null
    }
}
