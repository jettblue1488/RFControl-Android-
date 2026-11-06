package com.rfcontrol.app

object Signal {
    const val WARNING = "RSSI is signal strength, not a measurement of distance. It cannot establish exact distance or direction. Walls, your body, interference and transmit power affect readings. A strong signal does not prove a device is on you or connected to you."
    fun valid(rssi: Int) = rssi in -126..-1
    fun label(rssi: Int): String = when {
        !valid(rssi) -> "Signal unavailable"
        rssi >= -55 -> "Strong signal — may be nearby"
        rssi >= -70 -> "Moderate signal"
        else -> "Weak signal"
    }
    fun interval(rssi: Int): Long = (250L + ((-rssi.coerceIn(-100, -40) - 40) * 2750L / 60))
    fun fresh(lastSeen: Long, now: Long) = lastSeen > 0 && now - lastSeen in 0..5000
    fun smooth(previous: Double?, rssi: Int) = previous?.let { it * 0.65 + rssi * 0.35 } ?: rssi.toDouble()
}
