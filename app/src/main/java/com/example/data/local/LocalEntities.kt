package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whatsapp_notifications")
data class WhatsAppNotificationEntity(
    @PrimaryKey val notificationKey: String,
    val conversationKey: String,
    val senderName: String,
    val messagePreview: String,
    val timestampMs: Long,
    val packageName: String,
    val active: Boolean = true,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "wa_business_threads")
data class WhatsAppBusinessThreadEntity(
    @PrimaryKey val phoneNumber: String,
    val contactName: String,
    val lastMessagePreview: String,
    val lastMessageAtMs: Long,
    val lastInboundAtMs: Long
)

@Entity(tableName = "wa_business_messages")
data class WhatsAppBusinessMessageEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val contactName: String,
    val direction: String, // INBOUND or OUTBOUND
    val messageType: String, // TEXT, TEMPLATE, IMAGE, DOCUMENT, VIDEO
    val body: String,
    val templateName: String? = null,
    val mediaUrl: String? = null,
    val status: String = "SENT",
    val timestampMs: Long
)
