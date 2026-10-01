package br.com.jcdecor.tracker.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object Iso8601 {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun formatUtc(epochMs: Long): String = formatter.format(Instant.ofEpochMilli(epochMs))
}
