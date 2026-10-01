package br.com.jcdecor.tracker.util

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Formats {
    fun accuracy(value: Float): String {
        val text = String.format(Locale.US, "%.1f", value)
        return if (text.endsWith(".0")) text.dropLast(2) else text
    }

    fun coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

    fun speedKmh(metersPerSecond: Float?): String {
        if (metersPerSecond == null || metersPerSecond < 0f) return "—"
        val kmh = metersPerSecond * 3.6
        return String.format(Locale("pt", "BR"), "%.0f km/h", kmh)
    }
}

object Geo {
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * earth * atan2(sqrt(a), sqrt(1 - a))
    }
}
