package com.example.whatsapp

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.local.ChatXDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.WhatsAppNotificationEntity
import com.example.security.E2eeCryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Personal WhatsApp Integration (Android NotificationListenerService):
 * - Captures ONLY explicit OS notification previews posted by WhatsApp when enabled by the user.
 * - Handles notification updates and removals cleanly.
 * - Does NOT access WhatsApp private databases, bypass E2EE, sync full history, or auto-reply.
 */
class ChatXNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val WHATSAPP_PACKAGE = "com.whatsapp"

        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val componentName = ComponentName(context, ChatXNotificationListenerService::class.java)
            return enabledListeners.split(":").any { entry ->
                ComponentName.unflattenFromString(entry) == componentName
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != WHATSAPP_PACKAGE) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Ignore summary group notifications without individual message content
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val preview = (bigText?.takeIf { it.isNotBlank() } ?: text.orEmpty()).trim()

        if (title.isBlank() || preview.isBlank()) return
        // Skip generic WhatsApp status notifications ("Checking for new messages", "X messages from Y chats")
        if (title.equals("WhatsApp", ignoreCase = true) && preview.contains("messages from", ignoreCase = true)) {
            return
        }

        val conversationKey = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { sbn.key }

        serviceScope.launch {
            val prefs = PreferencesManager(applicationContext, E2eeCryptoManager())
            if (!prefs.isPersonalWaEnabledOnce()) return@launch

            val dao = ChatXDatabase.getInstance(applicationContext).whatsAppNotificationDao()
            dao.upsertNotification(
                WhatsAppNotificationEntity(
                    notificationKey = "${sbn.key}_${sbn.postTime}",
                    conversationKey = conversationKey,
                    senderName = title,
                    messagePreview = preview,
                    timestampMs = sbn.postTime,
                    packageName = sbn.packageName,
                    active = true,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != WHATSAPP_PACKAGE) return
        serviceScope.launch {
            val dao = ChatXDatabase.getInstance(applicationContext).whatsAppNotificationDao()
            dao.markNotificationRemoved("${sbn.key}_${sbn.postTime}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
