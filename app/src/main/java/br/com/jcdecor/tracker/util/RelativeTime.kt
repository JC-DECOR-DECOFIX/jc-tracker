package br.com.jcdecor.tracker.util

object RelativeTime {
    fun updateLabel(atEpochMs: Long?, nowEpochMs: Long): String {
        if (atEpochMs == null) return "Aguardando primeira posição"
        val seconds = ((nowEpochMs - atEpochMs) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 5 -> "Última atualização agora"
            seconds < 60 -> "Última atualização há $seconds s"
            else -> "Última atualização há ${seconds / 60} min"
        }
    }

    fun notificationLabel(atEpochMs: Long?, nowEpochMs: Long): String {
        if (atEpochMs == null) return "Aguardando primeira posição"
        val seconds = ((nowEpochMs - atEpochMs) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 5 -> "Última posição agora"
            seconds < 60 -> "Última posição há $seconds segundos"
            else -> "Última posição há ${seconds / 60} min"
        }
    }
}
