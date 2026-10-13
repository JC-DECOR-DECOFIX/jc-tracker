package br.com.jcdecor.tracker.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Credencial `jctdev_` no Android Keystore, via EncryptedSharedPreferences.
 * O commit substitui o token anterior numa escrita só.
 */
class EncryptedDeviceCredentialStore(
    context: Context,
) : DeviceCredentialStore {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun read(): CredentialRecord = CredentialRecord(
        token = prefs.getString(KEY_TOKEN, null),
        expiresAtEpochMs = prefs.getLong(KEY_EXPIRES, ABSENT).takeIf { it >= 0L },
        refreshAfterEpochMs = prefs.getLong(KEY_REFRESH, ABSENT).takeIf { it >= 0L },
        revoked = prefs.getBoolean(KEY_REVOKED, false),
    )

    override fun save(token: String, expiresAtEpochMs: Long?, refreshAfterEpochMs: Long?) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES, expiresAtEpochMs ?: ABSENT)
            .putLong(KEY_REFRESH, refreshAfterEpochMs ?: ABSENT)
            .putBoolean(KEY_REVOKED, false)
            .commit()
    }

    override fun markRevoked() {
        prefs.edit().putBoolean(KEY_REVOKED, true).commit()
    }

    private companion object {
        const val FILE_NAME = "jc_tracker_device_credential"
        const val KEY_TOKEN = "device_access_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_REFRESH = "refresh_after"
        const val KEY_REVOKED = "revoked"
        const val ABSENT = -1L
    }
}
