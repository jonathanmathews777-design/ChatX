package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.security.E2eeCryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.chatXDataStore: DataStore<Preferences> by preferencesDataStore(name = "chatx_prefs")

data class BusinessWhatsAppConfig(
    val enabled: Boolean = false,
    val phoneNumberId: String = "",
    val wabaId: String = "",
    val webhookRelayUrl: String = "",
    val accessToken: String = ""
)

class PreferencesManager(
    private val context: Context,
    private val cryptoManager: E2eeCryptoManager
) {
    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val PERSONAL_WA_ENABLED = booleanPreferencesKey("personal_wa_enabled")
        val BUSINESS_WA_ENABLED = booleanPreferencesKey("business_wa_enabled")
        val BUSINESS_PHONE_NUMBER_ID = stringPreferencesKey("business_phone_number_id")
        val BUSINESS_WABA_ID = stringPreferencesKey("business_waba_id")
        val BUSINESS_WEBHOOK_URL = stringPreferencesKey("business_webhook_url")
        val BUSINESS_ACCESS_TOKEN_WRAPPED = stringPreferencesKey("business_access_token_wrapped")
        fun wrappedPrivKey(uid: String) = stringPreferencesKey("e2ee_priv_wrapped_$uid")
        fun unlockedUsername(uid: String) = stringPreferencesKey("unlocked_username_$uid")
    }

    val darkThemeFlow: Flow<Boolean> = context.chatXDataStore.data.map { it[Keys.DARK_THEME] ?: true }

    val personalWaEnabledFlow: Flow<Boolean> = context.chatXDataStore.data.map {
        it[Keys.PERSONAL_WA_ENABLED] ?: false
    }

    val businessWaConfigFlow: Flow<BusinessWhatsAppConfig> = context.chatXDataStore.data.map { prefs ->
        val wrappedToken = prefs[Keys.BUSINESS_ACCESS_TOKEN_WRAPPED]
        val plainToken = if (!wrappedToken.isNullOrBlank()) {
            cryptoManager.unwrapSecretString(wrappedToken).orEmpty()
        } else {
            ""
        }
        BusinessWhatsAppConfig(
            enabled = prefs[Keys.BUSINESS_WA_ENABLED] ?: false,
            phoneNumberId = prefs[Keys.BUSINESS_PHONE_NUMBER_ID].orEmpty(),
            wabaId = prefs[Keys.BUSINESS_WABA_ID].orEmpty(),
            webhookRelayUrl = prefs[Keys.BUSINESS_WEBHOOK_URL].orEmpty(),
            accessToken = plainToken
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.chatXDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setPersonalWaEnabled(enabled: Boolean) {
        context.chatXDataStore.edit { it[Keys.PERSONAL_WA_ENABLED] = enabled }
    }

    suspend fun isPersonalWaEnabledOnce(): Boolean {
        return context.chatXDataStore.data.first()[Keys.PERSONAL_WA_ENABLED] ?: false
    }

    suspend fun saveBusinessWaConfig(
        enabled: Boolean,
        phoneNumberId: String,
        wabaId: String,
        webhookRelayUrl: String,
        accessToken: String
    ) {
        val wrappedToken = if (accessToken.isNotBlank()) {
            cryptoManager.wrapSecretString(accessToken.trim())
        } else {
            ""
        }
        context.chatXDataStore.edit { prefs ->
            prefs[Keys.BUSINESS_WA_ENABLED] = enabled
            prefs[Keys.BUSINESS_PHONE_NUMBER_ID] = phoneNumberId.trim()
            prefs[Keys.BUSINESS_WABA_ID] = wabaId.trim()
            prefs[Keys.BUSINESS_WEBHOOK_URL] = webhookRelayUrl.trim()
            prefs[Keys.BUSINESS_ACCESS_TOKEN_WRAPPED] = wrappedToken
        }
    }

    suspend fun setBusinessWaEnabled(enabled: Boolean) {
        context.chatXDataStore.edit { it[Keys.BUSINESS_WA_ENABLED] = enabled }
    }

    suspend fun saveWrappedPrivateKey(uid: String, wrappedBase64: String) {
        context.chatXDataStore.edit { it[Keys.wrappedPrivKey(uid)] = wrappedBase64 }
    }

    suspend fun getWrappedPrivateKey(uid: String): String? {
        return context.chatXDataStore.data.first()[Keys.wrappedPrivKey(uid)]
    }

    suspend fun setSessionUnlocked(uid: String, username: String?) {
        context.chatXDataStore.edit { prefs ->
            if (username == null) {
                prefs.remove(Keys.unlockedUsername(uid))
            } else {
                prefs[Keys.unlockedUsername(uid)] = username
            }
        }
    }

    fun observeSessionUnlocked(uid: String): Flow<String?> {
        return context.chatXDataStore.data.map { it[Keys.unlockedUsername(uid)] }
    }
}
