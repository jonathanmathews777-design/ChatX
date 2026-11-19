package com.example.whatsapp

import com.example.data.local.PreferencesManager
import com.example.data.local.WhatsAppBusinessDao
import com.example.data.local.WhatsAppBusinessMessageEntity
import com.example.data.local.WhatsAppBusinessThreadEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Official WhatsApp Business Platform & Cloud API repository.
 * Enforces Meta's 24-hour customer service window for free-form replies,
 * supports approved Template Messages and Media messages, and pulls inbound
 * webhook events from a user-configured secure backend webhook relay.
 */
class WhatsAppBusinessRepository(
    private val dao: WhatsAppBusinessDao,
    private val prefs: PreferencesManager,
    private val api: MetaWhatsAppCloudService = WhatsAppBusinessApiClient.create()
) {
    companion object {
        const val CUSTOMER_SERVICE_WINDOW_MS = 24L * 60L * 60L * 1000L
    }

    fun observeThreads(): Flow<List<WhatsAppBusinessThreadEntity>> = dao.observeThreads()

    fun observeThread(phoneNumber: String): Flow<WhatsAppBusinessThreadEntity?> = dao.observeThread(phoneNumber)

    fun observeMessages(phoneNumber: String): Flow<List<WhatsAppBusinessMessageEntity>> = dao.observeMessages(phoneNumber)

    fun isCustomerServiceWindowOpen(thread: WhatsAppBusinessThreadEntity?): Boolean {
        if (thread == null || thread.lastInboundAtMs <= 0L) return false
        return (System.currentTimeMillis() - thread.lastInboundAtMs) <= CUSTOMER_SERVICE_WINDOW_MS
    }

    suspend fun sendFreeFormTextReply(
        recipientPhone: String,
        contactName: String,
        body: String
    ): Result<Unit> {
        return try {
            val config = prefs.businessWaConfigFlow.first()
            require(config.enabled) { "WhatsApp Business integration is disabled." }
            require(config.phoneNumberId.isNotBlank() && config.accessToken.isNotBlank()) {
                "Configure Phone Number ID and Cloud API Access Token in Settings."
            }
            val thread = dao.observeThread(recipientPhone).first()
            if (!isCustomerServiceWindowOpen(thread)) {
                return Result.failure(
                    IllegalStateException("24-hour customer service window is closed. Send an approved Template Message instead.")
                )
            }

            val cleanPhone = recipientPhone.filter { it.isDigit() }
            val response = api.sendMessage(
                phoneNumberId = config.phoneNumberId,
                bearerToken = "Bearer ${config.accessToken}",
                payload = CloudApiSendMessageRequest(
                    to = cleanPhone,
                    type = "text",
                    text = CloudApiTextBody(body = body.trim())
                )
            )
            val msgId = response.messages.firstOrNull()?.id ?: "out_${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            dao.upsertMessage(
                WhatsAppBusinessMessageEntity(
                    id = msgId,
                    phoneNumber = cleanPhone,
                    contactName = contactName,
                    direction = "OUTBOUND",
                    messageType = "TEXT",
                    body = body.trim(),
                    status = "SENT",
                    timestampMs = now
                )
            )
            dao.upsertThread(
                WhatsAppBusinessThreadEntity(
                    phoneNumber = cleanPhone,
                    contactName = contactName,
                    lastMessagePreview = body.trim(),
                    lastMessageAtMs = now,
                    lastInboundAtMs = thread?.lastInboundAtMs ?: 0L
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTemplateMessage(
        recipientPhone: String,
        contactName: String,
        templateName: String,
        languageCode: String = "en_US"
    ): Result<Unit> {
        return try {
            val config = prefs.businessWaConfigFlow.first()
            require(config.enabled) { "WhatsApp Business integration is disabled." }
            require(config.phoneNumberId.isNotBlank() && config.accessToken.isNotBlank()) {
                "Configure Phone Number ID and Cloud API Access Token in Settings."
            }
            val cleanPhone = recipientPhone.filter { it.isDigit() }
            require(cleanPhone.length >= 7) { "Enter a valid E.164 recipient phone number." }
            require(templateName.isNotBlank()) { "Template name is required." }

            val response = api.sendMessage(
                phoneNumberId = config.phoneNumberId,
                bearerToken = "Bearer ${config.accessToken}",
                payload = CloudApiSendMessageRequest(
                    to = cleanPhone,
                    type = "template",
                    template = CloudApiTemplateBody(
                        name = templateName.trim(),
                        language = CloudApiTemplateLanguage(code = languageCode.trim().ifBlank { "en_US" })
                    )
                )
            )
            val msgId = response.messages.firstOrNull()?.id ?: "tpl_${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            val existing = dao.observeThread(cleanPhone).first()
            dao.upsertMessage(
                WhatsAppBusinessMessageEntity(
                    id = msgId,
                    phoneNumber = cleanPhone,
                    contactName = contactName.ifBlank { "+$cleanPhone" },
                    direction = "OUTBOUND",
                    messageType = "TEMPLATE",
                    body = "Template: ${templateName.trim()}",
                    templateName = templateName.trim(),
                    status = "SENT",
                    timestampMs = now
                )
            )
            dao.upsertThread(
                WhatsAppBusinessThreadEntity(
                    phoneNumber = cleanPhone,
                    contactName = contactName.ifBlank { existing?.contactName ?: "+$cleanPhone" },
                    lastMessagePreview = "Template: ${templateName.trim()}",
                    lastMessageAtMs = now,
                    lastInboundAtMs = existing?.lastInboundAtMs ?: 0L
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMediaMessage(
        recipientPhone: String,
        contactName: String,
        mediaType: String, // "image", "document", "video"
        mediaUrl: String,
        caption: String?
    ): Result<Unit> {
        return try {
            val config = prefs.businessWaConfigFlow.first()
            require(config.enabled) { "WhatsApp Business integration is disabled." }
            require(config.phoneNumberId.isNotBlank() && config.accessToken.isNotBlank()) {
                "Configure Phone Number ID and Cloud API Access Token in Settings."
            }
            val thread = dao.observeThread(recipientPhone).first()
            if (!isCustomerServiceWindowOpen(thread)) {
                return Result.failure(
                    IllegalStateException("24-hour customer service window is closed. Media replies require an active customer window.")
                )
            }
            val cleanPhone = recipientPhone.filter { it.isDigit() }
            val link = CloudApiMediaLink(link = mediaUrl.trim(), caption = caption?.trim()?.takeIf { it.isNotEmpty() })
            val request = when (mediaType.lowercase()) {
                "image" -> CloudApiSendMessageRequest(to = cleanPhone, type = "image", image = link)
                "video" -> CloudApiSendMessageRequest(to = cleanPhone, type = "video", video = link)
                else -> CloudApiSendMessageRequest(to = cleanPhone, type = "document", document = link)
            }
            val response = api.sendMessage(
                phoneNumberId = config.phoneNumberId,
                bearerToken = "Bearer ${config.accessToken}",
                payload = request
            )
            val msgId = response.messages.firstOrNull()?.id ?: "med_${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            dao.upsertMessage(
                WhatsAppBusinessMessageEntity(
                    id = msgId,
                    phoneNumber = cleanPhone,
                    contactName = contactName,
                    direction = "OUTBOUND",
                    messageType = mediaType.uppercase(),
                    body = caption?.takeIf { it.isNotBlank() } ?: "Sent ${mediaType.lowercase()}",
                    mediaUrl = mediaUrl.trim(),
                    status = "SENT",
                    timestampMs = now
                )
            )
            dao.upsertThread(
                WhatsAppBusinessThreadEntity(
                    phoneNumber = cleanPhone,
                    contactName = contactName,
                    lastMessagePreview = "Media (${mediaType.lowercase()})",
                    lastMessageAtMs = now,
                    lastInboundAtMs = thread?.lastInboundAtMs ?: 0L
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncWebhookRelay(): Result<Int> {
        return try {
            val config = prefs.businessWaConfigFlow.first()
            require(config.enabled) { "WhatsApp Business integration is disabled." }
            require(config.webhookRelayUrl.startsWith("https://")) {
                "Configure an HTTPS backend webhook relay URL in Settings."
            }
            val response = api.fetchWebhookEvents(config.webhookRelayUrl)
            response.events.forEach { event ->
                val cleanPhone = event.from.filter { it.isDigit() }
                val existing = dao.observeThread(cleanPhone).first()
                dao.upsertMessage(
                    WhatsAppBusinessMessageEntity(
                        id = event.id,
                        phoneNumber = cleanPhone,
                        contactName = event.contactName ?: existing?.contactName ?: "+$cleanPhone",
                        direction = "INBOUND",
                        messageType = event.type.uppercase(),
                        body = event.body,
                        mediaUrl = event.mediaUrl,
                        status = "RECEIVED",
                        timestampMs = event.timestampMs
                    )
                )
                val latestInbound = maxOf(existing?.lastInboundAtMs ?: 0L, event.timestampMs)
                dao.upsertThread(
                    WhatsAppBusinessThreadEntity(
                        phoneNumber = cleanPhone,
                        contactName = event.contactName ?: existing?.contactName ?: "+$cleanPhone",
                        lastMessagePreview = event.body,
                        lastMessageAtMs = maxOf(existing?.lastMessageAtMs ?: 0L, event.timestampMs),
                        lastInboundAtMs = latestInbound
                    )
                )
            }
            Result.success(response.events.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllBusinessData() {
        dao.clearMessages()
        dao.clearThreads()
    }
}
