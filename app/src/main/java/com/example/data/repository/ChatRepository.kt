package com.example.data.repository

import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.local.PreferencesManager
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ConversationType
import com.example.data.model.DecryptedChatMessage
import com.example.data.model.DeliveryStatus
import com.example.data.model.MemberProfile
import com.example.data.model.MessageType
import com.example.security.E2eeCryptoManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val prefs: PreferencesManager,
    private val cryptoManager: E2eeCryptoManager
) {
    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val path = "conversations"
        emitAll(
            db.collection("conversations")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeConversation(conversationId: String, uid: String): Flow<Conversation?> = flow {
        val path = "conversations/$conversationId"
        emitAll(
            db.collection("conversations").document(conversationId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(conversationId: String, uid: String): Flow<List<ChatMessage>> = flow {
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId)
                .collection("messages")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    val now = Date()
                    snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { msg ->
                            val exp = msg.expiresAt?.toDate()
                            exp == null || exp.after(now)
                        }
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun decryptMessages(
        conversation: Conversation,
        messages: List<ChatMessage>,
        myUid: String,
        participantsByUid: Map<String, MemberProfile>
    ): List<DecryptedChatMessage> {
        val wrappedPriv = prefs.getWrappedPrivateKey(myUid)
        return messages.map { msg ->
            if (msg.deleted) {
                DecryptedChatMessage(msg, "Message deleted", true)
            } else {
                val plain = when (conversation.parsedType) {
                    ConversationType.DIRECT -> {
                        val peerUid = conversation.participantUids.firstOrNull { it != myUid } ?: myUid
                        val peerPubKey = participantsByUid[peerUid]?.publicKeyBase64
                        if (wrappedPriv != null && !peerPubKey.isNullOrBlank()) {
                            cryptoManager.decryptForDirectConversation(
                                ciphertextBase64 = msg.ciphertextBase64,
                                ivBase64 = msg.ivBase64,
                                conversationId = conversation.id,
                                wrappedMyPrivateKeyBase64 = wrappedPriv,
                                peerPublicKeyBase64 = peerPubKey
                            )
                        } else null
                    }
                    ConversationType.GROUP -> {
                        val pubKeys = conversation.participantUids.mapNotNull { participantsByUid[it]?.publicKeyBase64 }
                        if (pubKeys.isNotEmpty()) {
                            cryptoManager.decryptForGroupConversation(
                                ciphertextBase64 = msg.ciphertextBase64,
                                ivBase64 = msg.ivBase64,
                                conversationId = conversation.id,
                                sortedParticipantPublicKeys = pubKeys
                            )
                        } else null
                    }
                }
                if (plain != null) {
                    DecryptedChatMessage(msg, plain, true)
                } else {
                    DecryptedChatMessage(msg, "[Encrypted payload — key verification pending]", false)
                }
            }
        }
    }

    suspend fun createOrOpenDirectConversation(
        myProfile: MemberProfile,
        peerProfile: MemberProfile
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated."))
        val sorted = listOf(myProfile.uid, peerProfile.uid).sorted()
        val convId = "dm_${sorted[0]}_${sorted[1]}".take(120)
        val path = "conversations/$convId"
        return try {
            val docRef = db.collection("conversations").document(convId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                return Result.success(convId)
            }
            val payload = mapOf(
                "type" to ConversationType.DIRECT.name,
                "title" to peerProfile.displayName,
                "participantUids" to sorted,
                "adminUid" to uid,
                "lastMessagePreview" to "E2EE channel initialized",
                "disappearingSeconds" to 0L,
                "typingUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createGroupConversation(
        myProfile: MemberProfile,
        title: String,
        peers: List<MemberProfile>
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated."))
        val convId = "grp_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "conversations/$convId"
        return try {
            val cleanTitle = title.trim().take(80)
            require(cleanTitle.isNotEmpty()) { "Group name is required." }
            val participants = (listOf(myProfile.uid) + peers.map { it.uid }).distinct().take(50)
            val payload = mapOf(
                "type" to ConversationType.GROUP.name,
                "title" to cleanTitle,
                "participantUids" to participants,
                "adminUid" to uid,
                "lastMessagePreview" to "E2EE group created",
                "disappearingSeconds" to 0L,
                "typingUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("conversations").document(convId).set(payload).await()
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendEncryptedMessage(
        conversation: Conversation,
        myProfile: MemberProfile,
        participantsByUid: Map<String, MemberProfile>,
        plaintext: String,
        messageType: MessageType = MessageType.TEXT,
        attachmentName: String? = null,
        attachmentMimeType: String? = null,
        attachmentSizeBytes: Long? = null,
        voiceDurationMs: Long? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated."))
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "conversations/${conversation.id}/messages/$msgId"
        return try {
            val cleanText = plaintext.trim()
            require(cleanText.isNotEmpty()) { "Message content cannot be empty." }

            val encrypted = encryptForConversation(conversation, myProfile.uid, participantsByUid, cleanText)
                ?: return Result.failure(IllegalStateException("Missing E2EE key material for participant."))

            val expiresAt = if (conversation.disappearingSeconds > 0L) {
                Timestamp(Date(System.currentTimeMillis() + conversation.disappearingSeconds * 1000L))
            } else null

            val payload = mutableMapOf<String, Any?>(
                "conversationId" to conversation.id,
                "senderUid" to uid,
                "senderUsername" to myProfile.username,
                "participantUids" to conversation.participantUids,
                "messageType" to messageType.name,
                "ciphertextBase64" to encrypted.ciphertextBase64,
                "ivBase64" to encrypted.ivBase64,
                "encryptionScheme" to E2eeCryptoManager.SCHEME_ID,
                "attachmentName" to attachmentName?.take(200),
                "attachmentMimeType" to attachmentMimeType?.take(120),
                "attachmentSizeBytes" to attachmentSizeBytes,
                "voiceDurationMs" to voiceDurationMs,
                "deliveryStatus" to DeliveryStatus.SENT.name,
                "readByUids" to listOf(uid),
                "edited" to false,
                "deleted" to false,
                "expiresAt" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val previewLabel = when (messageType) {
                MessageType.TEXT -> "E2EE • Encrypted message"
                MessageType.IMAGE -> "E2EE • Image (${attachmentName ?: "media"})"
                MessageType.VIDEO -> "E2EE • Video (${attachmentName ?: "media"})"
                MessageType.DOCUMENT -> "E2EE • Document (${attachmentName ?: "file"})"
                MessageType.VOICE -> "E2EE • Voice note"
            }.take(190)

            val batch = db.batch()
            batch.set(
                db.collection("conversations").document(conversation.id)
                    .collection("messages").document(msgId),
                payload
            )
            batch.update(
                db.collection("conversations").document(conversation.id),
                mapOf(
                    "lastMessagePreview" to previewLabel,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "typingUids" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun editMessage(
        conversation: Conversation,
        message: ChatMessage,
        myUid: String,
        participantsByUid: Map<String, MemberProfile>,
        newPlaintext: String
    ): Result<Unit> {
        val path = "conversations/${conversation.id}/messages/${message.id}"
        return try {
            require(message.senderUid == myUid) { "Only the sender can edit this message." }
            val encrypted = encryptForConversation(conversation, myUid, participantsByUid, newPlaintext.trim())
                ?: return Result.failure(IllegalStateException("Unable to re-encrypt edited message."))

            db.collection("conversations").document(conversation.id)
                .collection("messages").document(message.id)
                .update(
                    mapOf(
                        "ciphertextBase64" to encrypted.ciphertextBase64,
                        "ivBase64" to encrypted.ivBase64,
                        "edited" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(
        conversation: Conversation,
        message: ChatMessage,
        myUid: String,
        participantsByUid: Map<String, MemberProfile>
    ): Result<Unit> {
        val path = "conversations/${conversation.id}/messages/${message.id}"
        return try {
            require(message.senderUid == myUid) { "Only the sender can delete this message." }
            val encryptedTombstone = encryptForConversation(conversation, myUid, participantsByUid, "Message deleted")
                ?: E2eeCryptoManager.EncryptedPayload("ZGVsZXRlZA==", "MTIzNDU2Nzg5MDEy")

            db.collection("conversations").document(conversation.id)
                .collection("messages").document(message.id)
                .update(
                    mapOf(
                        "ciphertextBase64" to encryptedTombstone.ciphertextBase64,
                        "ivBase64" to encryptedTombstone.ivBase64,
                        "deleted" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markUnreadMessagesRead(
        conversationId: String,
        messages: List<ChatMessage>,
        myUid: String
    ) {
        val unread = messages.filter { it.senderUid != myUid && myUid !in it.readByUids }
        if (unread.isEmpty()) return
        try {
            val batch = db.batch()
            unread.take(25).forEach { msg ->
                val ref = db.collection("conversations").document(conversationId)
                    .collection("messages").document(msg.id)
                batch.update(
                    ref,
                    mapOf(
                        "deliveryStatus" to DeliveryStatus.READ.name,
                        "readByUids" to FieldValue.arrayUnion(myUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        } catch (_: Exception) {
            // Non-fatal read receipt update
        }
    }

    suspend fun setTyping(conversationId: String, myUid: String, isTyping: Boolean) {
        try {
            val op = if (isTyping) FieldValue.arrayUnion(myUid) else FieldValue.arrayRemove(myUid)
            db.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "typingUids" to op,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (_: Exception) {
            // Non-fatal typing indicator
        }
    }

    suspend fun setDisappearingTimer(conversationId: String, seconds: Long): Result<Unit> {
        val path = "conversations/$conversationId"
        return try {
            db.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "disappearingSeconds" to seconds,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun encryptForConversation(
        conversation: Conversation,
        myUid: String,
        participantsByUid: Map<String, MemberProfile>,
        plaintext: String
    ): E2eeCryptoManager.EncryptedPayload? {
        val wrappedPriv = prefs.getWrappedPrivateKey(myUid) ?: return null
        return when (conversation.parsedType) {
            ConversationType.DIRECT -> {
                val peerUid = conversation.participantUids.firstOrNull { it != myUid } ?: myUid
                val peerPub = participantsByUid[peerUid]?.publicKeyBase64 ?: return null
                cryptoManager.encryptForDirectConversation(
                    plaintext = plaintext,
                    conversationId = conversation.id,
                    wrappedMyPrivateKeyBase64 = wrappedPriv,
                    peerPublicKeyBase64 = peerPub
                )
            }
            ConversationType.GROUP -> {
                val pubKeys = conversation.participantUids.mapNotNull { participantsByUid[it]?.publicKeyBase64 }
                if (pubKeys.isEmpty()) return null
                cryptoManager.encryptForGroupConversation(
                    plaintext = plaintext,
                    conversationId = conversation.id,
                    sortedParticipantPublicKeys = pubKeys
                )
            }
        }
    }
}
