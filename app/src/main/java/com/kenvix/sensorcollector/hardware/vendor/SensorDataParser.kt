//--------------------------------------------------
// Interface SensorDataParser
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.hardware.vendor

interface SensorDataParser {
    fun onDataInput(data: ByteArray, onReceived: (SensorData) -> Unit)
    val packetHeader: ByteArray
}
