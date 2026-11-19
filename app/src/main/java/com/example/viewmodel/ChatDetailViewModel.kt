package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.DecryptedChatMessage
import com.example.data.model.MemberProfile
import com.example.data.model.MessageType
import com.example.data.model.UiState
import com.example.data.repository.AuthAndAdminRepository
import com.example.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatDetailState(
    val conversation: Conversation,
    val messages: List<DecryptedChatMessage>,
    val participantsByUid: Map<String, MemberProfile>
)

class ChatDetailViewModel(
    private val chatRepo: ChatRepository,
    authRepo: AuthAndAdminRepository,
    private val conversationId: String,
    private val currentUserId: String
) : ViewModel() {

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    // Periodic tick every 5s so disappearing messages drop out of UI immediately on expiry
    private val expiryTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(5000L)
        }
    }

    val uiState: StateFlow<UiState<ChatDetailState>> = combine(
        chatRepo.observeConversation(conversationId, currentUserId),
        chatRepo.observeMessages(conversationId, currentUserId),
        authRepo.observeActiveMembers(currentUserId),
        expiryTicker
    ) { conversation, rawMessages, activeMembers, nowMs ->
        if (conversation == null) {
            UiState.Error("Conversation not found or access revoked.")
        } else {
            val participantsMap = activeMembers.associateBy { it.uid }
            val liveMessages = rawMessages.filter { msg ->
                val exp = msg.expiresAt?.toDate()?.time
                exp == null || exp > nowMs
            }
            val decrypted = chatRepo.decryptMessages(
                conversation = conversation,
                messages = liveMessages,
                myUid = currentUserId,
                participantsByUid = participantsMap
            )
            // Mark unread incoming messages read asynchronously
            if (liveMessages.any { it.senderUid != currentUserId && currentUserId !in it.readByUids }) {
                viewModelScope.launch {
                    chatRepo.markUnreadMessagesRead(conversation.id, liveMessages, currentUserId)
                }
            }
            UiState.Success(ChatDetailState(conversation, decrypted, participantsMap))
        }
    }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w("ChatDetailVM", "Error observing conversation", root)
            emit(UiState.Error(root.message ?: "Failed to load conversation"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun clearError() {
        _actionError.value = null
    }

    fun sendMessage(
        myProfile: MemberProfile,
        plaintext: String,
        messageType: MessageType = MessageType.TEXT,
        attachmentName: String? = null,
        attachmentMimeType: String? = null,
        attachmentSizeBytes: Long? = null,
        voiceDurationMs: Long? = null
    ) {
        val current = (uiState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _sending.value = true
            val res = chatRepo.sendEncryptedMessage(
                conversation = current.conversation,
                myProfile = myProfile,
                participantsByUid = current.participantsByUid,
                plaintext = plaintext,
                messageType = messageType,
                attachmentName = attachmentName,
                attachmentMimeType = attachmentMimeType,
                attachmentSizeBytes = attachmentSizeBytes,
                voiceDurationMs = voiceDurationMs
            )
            _sending.value = false
            res.onFailure { _actionError.value = it.message ?: "Failed to send encrypted message." }
        }
    }

    fun editMessage(message: ChatMessage, newPlaintext: String) {
        val current = (uiState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = chatRepo.editMessage(
                conversation = current.conversation,
                message = message,
                myUid = currentUserId,
                participantsByUid = current.participantsByUid,
                newPlaintext = newPlaintext
            )
            res.onFailure { _actionError.value = it.message ?: "Failed to edit message." }
        }
    }

    fun deleteMessage(message: ChatMessage) {
        val current = (uiState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = chatRepo.deleteMessage(
                conversation = current.conversation,
                message = message,
                myUid = currentUserId,
                participantsByUid = current.participantsByUid
            )
            res.onFailure { _actionError.value = it.message ?: "Failed to delete message." }
        }
    }

    fun setTyping(isTyping: Boolean) {
        viewModelScope.launch {
            chatRepo.setTyping(conversationId, currentUserId, isTyping)
        }
    }

    fun setDisappearingTimer(seconds: Long) {
        viewModelScope.launch {
            val res = chatRepo.setDisappearingTimer(conversationId, seconds)
            res.onFailure { _actionError.value = it.message ?: "Failed to update disappearing timer." }
        }
    }
}
