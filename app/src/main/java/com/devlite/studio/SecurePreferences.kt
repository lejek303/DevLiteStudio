package com.devlite.studio.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.devlite.studio.model.AiProvider

/**
 * Encrypted, on-device storage for user-supplied AI provider API keys.
 * Backed by AndroidX Security's EncryptedSharedPreferences, so keys are
 * never written to disk in plaintext and never leave the device except
 * in the direct HTTPS request to whichever provider the user selects.
 */
class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "devlite_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var anthropicApiKey: String?
        get() = prefs.getString(KEY_ANTHROPIC, null)
        set(value) = prefs.edit().putString(KEY_ANTHROPIC, value).apply()

    var openAiApiKey: String?
        get() = prefs.getString(KEY_OPENAI, null)
        set(value) = prefs.edit().putString(KEY_OPENAI, value).apply()

    var ollamaBaseUrl: String?
        get() = prefs.getString(KEY_OLLAMA_URL, "http://127.0.0.1:11434")
        set(value) = prefs.edit().putString(KEY_OLLAMA_URL, value).apply()

    var selectedProvider: AiProvider
        get() = AiProvider.valueOf(
            prefs.getString(KEY_PROVIDER, AiProvider.ANTHROPIC.name) ?: AiProvider.ANTHROPIC.name
        )
        set(value) = prefs.edit().putString(KEY_PROVIDER, value.name).apply()

    var selectedModel: String
        get() = prefs.getString(KEY_MODEL, "claude-sonnet-5") ?: "claude-sonnet-5"
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    fun clearAll() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ANTHROPIC = "anthropic_api_key"
        const val KEY_OPENAI = "openai_api_key"
        const val KEY_OLLAMA_URL = "ollama_base_url"
        const val KEY_PROVIDER = "selected_provider"
        const val KEY_MODEL = "selected_model"
    }
}
