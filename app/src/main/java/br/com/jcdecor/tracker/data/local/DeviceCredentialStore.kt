package br.com.jcdecor.tracker.data.local

import br.com.jcdecor.tracker.tracking.CredentialState

data class CredentialRecord(
    val token: String?,
    val expiresAtEpochMs: Long?,
    val refreshAfterEpochMs: Long?,
    val revoked: Boolean,
) {
    fun state(nowEpochMs: Long): CredentialState = when {
        revoked -> CredentialState.REVOKED
        token.isNullOrBlank() -> CredentialState.MISSING
        expiresAtEpochMs != null && nowEpochMs >= expiresAtEpochMs -> CredentialState.REFRESH_NEEDED
        refreshAfterEpochMs != null && nowEpochMs >= refreshAfterEpochMs -> CredentialState.REFRESH_NEEDED
        else -> CredentialState.VALID
    }

    fun registered(): Boolean = !token.isNullOrBlank() && !revoked

    override fun toString(): String = "CredentialRecord(state=${if (revoked) "revoked" else "stored"})"
}

interface DeviceCredentialStore {
    fun read(): CredentialRecord
    fun save(token: String, expiresAtEpochMs: Long?, refreshAfterEpochMs: Long?)
    fun markRevoked()
}

class MemoryCredentialStore(
    private var record: CredentialRecord = CredentialRecord(null, null, null, revoked = false),
) : DeviceCredentialStore {
    override fun read(): CredentialRecord = record

    override fun save(token: String, expiresAtEpochMs: Long?, refreshAfterEpochMs: Long?) {
        record = CredentialRecord(token, expiresAtEpochMs, refreshAfterEpochMs, revoked = false)
    }

    override fun markRevoked() {
        record = record.copy(revoked = true)
    }
}
