package com.kenvix.sensorcollector.utils

data class ThermometerData(
    val temperature: Double = Double.NaN,
    val humidity: Double = Double.NaN,
    val pressure: Double = Double.NaN,
    val batteryLevel: Float = Float.NaN,
    val batteryMV: Double = Double.NaN
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
