package br.com.jcdecor.tracker.data.remote

import br.com.jcdecor.tracker.tracking.UploadResult

object UploadResults {
    fun fromCode(code: Int): UploadResult = when (code) {
        in 200..299 -> UploadResult.Success
        408 -> UploadResult.Retryable("TIMEOUT")
        else -> UploadResult.Retryable("HTTP_$code")
    }

    fun network(): UploadResult = UploadResult.Retryable("NETWORK")
}

object ApiAuth {
    /** Nunca registre o retorno desta função. */
    fun bearer(token: String): String? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return null
        return "Bearer $trimmed"
    }
}
