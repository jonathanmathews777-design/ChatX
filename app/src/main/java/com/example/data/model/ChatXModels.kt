package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MemberRole {
    SUPER_ADMIN,
    MEMBER;

    companion object {
        fun fromRaw(raw: String): MemberRole =
            entries.firstOrNull { it.name == raw } ?: MEMBER
    }
}

enum class MemberStatus {
    ACTIVE,
    PENDING,
    SUSPENDED;

    companion object {
        fun fromRaw(raw: String): MemberStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class ConversationType {
    DIRECT,
    GROUP;

    companion object {
        fun fromRaw(raw: String): ConversationType =
            entries.firstOrNull { it.name == raw } ?: DIRECT
    }
}

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    DOCUMENT,
    VOICE;

    companion object {
        fun fromRaw(raw: String): MessageType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class DeliveryStatus {
    SENT,
    DELIVERED,
    READ;

    companion object {
        fun fromRaw(raw: String): DeliveryStatus =
            entries.firstOrNull { it.name == raw } ?: SENT
    }
}

@Keep
@IgnoreExtraProperties
data class MemberProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val role: String = MemberRole.MEMBER.name,
    val status: String = MemberStatus.PENDING.name,
    val publicKeyBase64: String = "",
    val keyFingerprint: String = "",
    val online: Boolean = false,
    val lastSeenAt: Timestamp? = null,
    val inviteCodeId: String? = null,
    val phoneE164: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedRole: MemberRole get() = MemberRole.fromRaw(role)
    val parsedStatus: MemberStatus get() = MemberStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class MemberSecret(
    @DocumentId val id: String = "",
    val uid: String = "",
    val username: String = "",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val email: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Invitation(
    @DocumentId val codeId: String = "",
    val createdByUid: String = "",
    val label: String = "",
    val autoApprove: Boolean = true,
    val used: Boolean = false,
    val revoked: Boolean = false,
    val usedByUid: String? = null,
    val usedByUsername: String? = null,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val type: String = ConversationType.DIRECT.name,
    val title: String = "",
    val participantUids: List<String> = emptyList(),
    val adminUid: String = "",
    val lastMessagePreview: String = "",
    val lastMessageAt: Timestamp? = null,
    val disappearingSeconds: Long = 0L,
    val typingUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: ConversationType get() = ConversationType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderUid: String = "",
    val senderUsername: String = "",
    val participantUids: List<String> = emptyList(),
    val messageType: String = MessageType.TEXT.name,
    val ciphertextBase64: String = "",
    val ivBase64: String = "",
    val encryptionScheme: String = "",
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentSizeBytes: Long? = null,
    val voiceDurationMs: Long? = null,
    val deliveryStatus: String = DeliveryStatus.SENT.name,
    val readByUids: List<String> = emptyList(),
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedMessageType: MessageType get() = MessageType.fromRaw(messageType)
    val parsedDeliveryStatus: DeliveryStatus get() = DeliveryStatus.fromRaw(deliveryStatus)
}

data class DecryptedChatMessage(
    val raw: ChatMessage,
    val plaintext: String,
    val decryptedSuccessfully: Boolean
)

enum class InboxFilter {
    ALL,
    CHATX,
    WHATSAPP
}

enum class ConversationSource {
    CHATX_E2EE,
    WHATSAPP_PERSONAL_PREVIEW,
    WHATSAPP_BUSINESS_CLOUD
}

data class UnifiedConversationItem(
    val id: String,
    val source: ConversationSource,
    val title: String,
    val subtitle: String,
    val timestampMs: Long,
    val badgeText: String,
    val activeIndicator: Boolean = false,
    val online: Boolean = false,
    val peerPhoneE164: String? = null,
    val peerUid: String? = null,
    val disappearingSeconds: Long = 0L
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
