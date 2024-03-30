package com.kenvix.sensorcollector.utils

data class ThermometerData(
    val temperature: Float = Float.NaN,
    val humidity: Float = Float.NaN,
    val pressure: Float = Float.NaN,
    val batteryLevel: Float = Float.NaN,
    val batteryMV: Float = Float.NaN
) {
    override fun toString(): String {
        return StringBuilder().let {
            if (temperature.isFinite()) it.append(String.format("🌡️ ${temperature}℃ "))
            if (humidity.isFinite()) it.append("💦 ${humidity}% ")
            if (pressure.isFinite()) it.append("🌬️ ${pressure}Pa")
            if (batteryMV.isFinite()) it.append("🔋 ${batteryLevel}% ${batteryMV}mV")
            it.toString()
        }
    }
}
