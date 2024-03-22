//--------------------------------------------------
// Class SensorData
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.sensorcollector.hardware.vendor

data class SensorData(
    /**
     * X 轴加速度
     */
    val accX: Double = Double.NaN,
    val accY: Double = Double.NaN,
    val accZ: Double = Double.NaN,
    /**
     * X 轴角速度
     */
    val gyroX: Double = Double.NaN,
    val gyroY: Double = Double.NaN,
    val gyroZ: Double = Double.NaN,
    /**
     * X 轴角度
     */
    val angleX: Double = Double.NaN,
    val angleY: Double = Double.NaN,
    val angleZ: Double = Double.NaN,
) {
    companion object {
        const val GRAVITY = 9.80665
    }
}
