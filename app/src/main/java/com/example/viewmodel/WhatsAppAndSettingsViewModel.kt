package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BusinessWhatsAppConfig
import com.example.data.local.PreferencesManager
import com.example.data.local.WhatsAppBusinessMessageEntity
import com.example.data.local.WhatsAppBusinessThreadEntity
import com.example.data.local.WhatsAppNotificationDao
import com.example.data.local.WhatsAppNotificationEntity
import com.example.data.repository.AuthAndAdminRepository
import com.example.security.E2eeCryptoManager
import com.example.whatsapp.WhatsAppBusinessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WhatsAppAndSettingsViewModel(
    private val prefs: PreferencesManager,
    private val waNotificationDao: WhatsAppNotificationDao,
    private val waBusinessRepo: WhatsAppBusinessRepository,
    private val authRepo: AuthAndAdminRepository,
    private val cryptoManager: E2eeCryptoManager
) : ViewModel() {

    val darkThemeEnabled: StateFlow<Boolean> = prefs.darkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val personalWaEnabled: StateFlow<Boolean> = prefs.personalWaEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val businessWaConfig: StateFlow<BusinessWhatsAppConfig> = prefs.businessWaConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), BusinessWhatsAppConfig())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _encryptedBackupJson = MutableStateFlow<String?>(null)
    val encryptedBackupJson: StateFlow<String?> = _encryptedBackupJson.asStateFlow()

    fun clearMessages() {
        _statusMessage.value = null
        _encryptedBackupJson.value = null
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkTheme(enabled) }
    }

    fun setPersonalWaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setPersonalWaEnabled(enabled)
            _statusMessage.value = if (enabled) {
                "Personal WhatsApp notification preview bridge enabled."
            } else {
                "Personal WhatsApp notification bridge disabled."
            }
        }
    }

    fun clearPersonalWaPreviews() {
        viewModelScope.launch {
            waNotificationDao.clearAll()
            _statusMessage.value = "Cleared local Personal WhatsApp notification previews."
        }
    }

    fun saveBusinessConfig(
        enabled: Boolean,
        phoneNumberId: String,
        wabaId: String,
        webhookRelayUrl: String,
        accessToken: String
    ) {
        viewModelScope.launch {
            prefs.saveBusinessWaConfig(enabled, phoneNumberId, wabaId, webhookRelayUrl, accessToken)
            _statusMessage.value = "WhatsApp Business Cloud API configuration saved."
        }
    }

    fun setBusinessWaEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setBusinessWaEnabled(enabled) }
    }

    fun syncBusinessWebhook() {
        viewModelScope.launch {
            _busy.value = true
            val res = waBusinessRepo.syncWebhookRelay()
            _busy.value = false
            res.onSuccess { count ->
                _statusMessage.value = "Synced $count webhook event(s) from backend relay."
            }
            res.onFailure {
                _statusMessage.value = it.message ?: "Webhook sync failed."
            }
        }
    }

    fun clearBusinessData() {
        viewModelScope.launch {
            waBusinessRepo.clearAllBusinessData()
            _statusMessage.value = "Cleared local WhatsApp Business threads."
        }
    }

    fun observePersonalThread(conversationKey: String): Flow<List<WhatsAppNotificationEntity>> =
        waNotificationDao.observeThreadMessages(conversationKey)

    fun observeBusinessThread(phoneNumber: String): Flow<WhatsAppBusinessThreadEntity?> =
        waBusinessRepo.observeThread(phoneNumber)

    fun observeBusinessMessages(phoneNumber: String): Flow<List<WhatsAppBusinessMessageEntity>> =
        waBusinessRepo.observeMessages(phoneNumber)

    fun isBusinessWindowOpen(thread: WhatsAppBusinessThreadEntity?): Boolean =
        waBusinessRepo.isCustomerServiceWindowOpen(thread)

    fun sendBusinessReply(phoneNumber: String, contactName: String, body: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = waBusinessRepo.sendFreeFormTextReply(phoneNumber, contactName, body)
            _busy.value = false
            res.onFailure { _statusMessage.value = it.message ?: "Failed to send Business reply." }
        }
    }

    fun sendBusinessTemplate(phoneNumber: String, contactName: String, templateName: String, languageCode: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = waBusinessRepo.sendTemplateMessage(phoneNumber, contactName, templateName, languageCode)
            _busy.value = false
            res.onSuccess { _statusMessage.value = "Template '$templateName' sent via Meta Cloud API." }
            res.onFailure { _statusMessage.value = it.message ?: "Failed to send Template message." }
        }
    }

    fun sendBusinessMedia(
        phoneNumber: String,
        contactName: String,
        mediaType: String,
        mediaUrl: String,
        caption: String?
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = waBusinessRepo.sendMediaMessage(phoneNumber, contactName, mediaType, mediaUrl, caption)
            _busy.value = false
            res.onSuccess { _statusMessage.value = "Media sent via Meta Cloud API." }
            res.onFailure { _statusMessage.value = it.message ?: "Failed to send Media message." }
        }
    }

    fun updateMyPhone(phoneE164: String) {
        viewModelScope.launch {
            val res = authRepo.updateMyPhoneE164(phoneE164)
            res.onSuccess { _statusMessage.value = "WhatsApp contact number updated." }
            res.onFailure { _statusMessage.value = it.message ?: "Failed to update number." }
        }
    }

    fun generateEncryptedBackup(username: String, keyFingerprint: String, passphrase: String) {
        if (passphrase.length < 8) {
            _statusMessage.value = "Backup passphrase must be at least 8 characters."
            return
        }
        val payload = """{"app":"ChatX","username":"$username","keyFingerprint":"$keyFingerprint","exportedAtMs":${System.currentTimeMillis()}}"""
        _encryptedBackupJson.value = cryptoManager.encryptBackupWithPassphrase(payload, passphrase.toCharArray())
        _statusMessage.value = "AES-256-GCM encrypted backup generated."
    }
}
