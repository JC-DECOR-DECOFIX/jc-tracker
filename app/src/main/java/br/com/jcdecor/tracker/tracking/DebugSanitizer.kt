package br.com.jcdecor.tracker.tracking

object DebugSanitizer {
    private val serviceCredential = Regex("jcsvc_[A-Za-z0-9_-]+")
    private val deviceCredential = Regex("jctdev_[A-Za-z0-9_-]+")
    private val authorization = Regex("(?i)authorization\\s*[:=]\\s*\\S+")
    private val bearer = Regex("(?i)bearer\\s+\\S+")

    fun sanitize(text: String): String = text
        .replace(serviceCredential, "jcsvc_<redacted>")
        .replace(deviceCredential, "jctdev_<redacted>")
        .replace(authorization, "authorization=<redacted>")
        .replace(bearer, "Bearer <redacted>")
}
