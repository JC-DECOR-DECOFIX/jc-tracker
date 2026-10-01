package br.com.jcdecor.tracker.tracking

object RouteId {
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 64) return null
        val ok = trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        return if (ok) trimmed else null
    }
}
