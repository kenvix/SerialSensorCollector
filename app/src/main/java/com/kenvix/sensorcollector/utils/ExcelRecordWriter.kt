//--------------------------------------------------
// Class ExcelRecordWriter
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.utils

import android.hardware.usb.UsbDevice
import android.net.Uri
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import com.kenvix.sensorcollector.hardware.vendor.SensorData
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Closeable

interface RecordWriter : Closeable {
    fun onSensorDataReceived(
        usbDevice: UsbDevice,
        usbSerialDevice: UsbSerialDevice,
        sensorData: SensorData
    )
}

class ExcelRecordWriter(val filePath: Uri) : RecordWriter,
    CoroutineScope by CoroutineScope(CoroutineName("ExcelRecordWriter")) {
    init {

    }

    override fun onSensorDataReceived(
        usbDevice: UsbDevice,
        usbSerialDevice: UsbSerialDevice,
        sensorData: SensorData
    ) {
        Log.d("ExcelRecordWriter", "SensorDataReceived: ${usbDevice.deviceName} : $sensorData")

    }

    override fun close() {

    }
}
