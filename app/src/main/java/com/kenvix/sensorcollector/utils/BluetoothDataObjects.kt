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
            if (temperature.isFinite()) it.append(String.format("🌡️ %.2f℃ ", temperature))
            if (humidity.isFinite()) it.append(String.format("💦 %02.2f%% ", humidity))
            if (pressure.isFinite()) it.append(String.format("🌬️ ${pressure}Pa"))
            if (batteryMV.isFinite()) it.append(String.format("🔋 %02.0f%% %02.0fmV", batteryLevel, batteryMV))
            it.toString()
        }
    }
}
