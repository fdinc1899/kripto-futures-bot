package com.fatih.futuresbot.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApiCredentials(val apiKey: String, val apiSecret: String) {
    // Log'a yanlışlıkla düşse bile anahtar açık görünmesin
    override fun toString(): String = "ApiCredentials(apiKey=${mask(apiKey)}, apiSecret=***)"

    companion object {
        fun mask(v: String): String = if (v.length <= 8) "***" else v.take(4) + "…" + v.takeLast(4)
    }
}

/** Diske yalnızca Keystore ile şifrelenmiş metin yazılır; düz metin asla saklanmaz. */
class CredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences("secure_credentials", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher()

    private val _hasCredentials = MutableStateFlow(
        prefs.contains(KEY_API) && prefs.contains(KEY_SECRET)
    )
    val hasCredentials: StateFlow<Boolean> = _hasCredentials.asStateFlow()

    fun save(credentials: ApiCredentials) {
        val ok = prefs.edit()
            .putString(KEY_API, cipher.encrypt(credentials.apiKey))
            .putString(KEY_SECRET, cipher.encrypt(credentials.apiSecret))
            .commit()
        check(ok) { "Kayıt diske yazılamadı" }
        _hasCredentials.value = true
    }

    /** Şifre çözülemezse (ör. Keystore anahtarı silinmiş) kayıt temizlenir ve null döner. */
    fun load(): ApiCredentials? {
        val k = prefs.getString(KEY_API, null) ?: return null
        val s = prefs.getString(KEY_SECRET, null) ?: return null
        return runCatching { ApiCredentials(cipher.decrypt(k), cipher.decrypt(s)) }
            .getOrElse {
                clear()
                null
            }
    }

    fun clear() {
        prefs.edit().clear().commit()
        runCatching { cipher.deleteKey() }
        _hasCredentials.value = false
    }

    private companion object {
        const val KEY_API = "api_key_enc"
        const val KEY_SECRET = "api_secret_enc"
    }
}
