package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.local.WhatsAppBusinessDao
import com.example.data.local.WhatsAppNotificationDao
import com.example.data.model.Conversation
import com.example.data.model.ConversationSource
import com.example.data.model.ConversationType
import com.example.data.model.InboxFilter
import com.example.data.model.MemberProfile
import com.example.data.model.UiState
import com.example.data.model.UnifiedConversationItem
import com.example.data.repository.AuthAndAdminRepository
import com.example.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(
    private val chatRepo: ChatRepository,
    private val authRepo: AuthAndAdminRepository,
    waNotificationDao: WhatsAppNotificationDao,
    waBusinessDao: WhatsAppBusinessDao,
    prefs: PreferencesManager,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(InboxFilter.ALL)
    val selectedFilter: StateFlow<InboxFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val activeMembers: StateFlow<List<MemberProfile>> = authRepo.observeActiveMembers(currentUserId)
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w("InboxVM", "Failed observing active members", fsErr ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unifiedConversations: StateFlow<UiState<List<UnifiedConversationItem>>> = combine(
        chatRepo.observeConversations(currentUserId),
        activeMembers,
        waNotificationDao.observeLatestThreads(),
        waBusinessDao.observeThreads(),
        prefs.personalWaEnabledFlow,
        prefs.businessWaConfigFlow,
        _selectedFilter,
        _searchQuery
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val conversations = values[0] as List<Conversation>
        @Suppress("UNCHECKED_CAST")
        val members = values[1] as List<MemberProfile>
        @Suppress("UNCHECKED_CAST")
        val personalWa = values[2] as List<com.example.data.local.WhatsAppNotificationEntity>
        @Suppress("UNCHECKED_CAST")
        val businessWa = values[3] as List<com.example.data.local.WhatsAppBusinessThreadEntity>
        val personalEnabled = values[4] as Boolean
        val businessConfig = values[5] as com.example.data.local.BusinessWhatsAppConfig
        val filter = values[6] as InboxFilter
        val query = (values[7] as String).trim()

        val membersByUid = members.associateBy { it.uid }

        val chatXItems = conversations.map { conv ->
            val peerUid = if (conv.parsedType == ConversationType.DIRECT) {
                conv.participantUids.firstOrNull { it != currentUserId }
            } else null
            val peer = peerUid?.let { membersByUid[it] }
            val title = if (conv.parsedType == ConversationType.DIRECT && peer != null) {
                peer.displayName
            } else {
                conv.title
            }
            val typingActive = conv.typingUids.any { it != currentUserId }
            val subtitle = if (typingActive) {
                "typing..."
            } else {
                conv.lastMessagePreview
            }
            val tsMs = (conv.lastMessageAt ?: conv.updatedAt ?: conv.createdAt)?.toDate()?.time ?: 0L
            UnifiedConversationItem(
                id = conv.id,
                source = ConversationSource.CHATX_E2EE,
                title = title,
                subtitle = subtitle,
                timestampMs = tsMs,
                badgeText = if (conv.parsedType == ConversationType.GROUP) "CHATX GROUP" else "CHATX E2EE",
                activeIndicator = typingActive,
                online = peer?.online ?: false,
                peerPhoneE164 = peer?.phoneE164,
                peerUid = peerUid,
                disappearingSeconds = conv.disappearingSeconds
            )
        }

        val personalItems = if (personalEnabled) {
            personalWa.map { wa ->
                UnifiedConversationItem(
                    id = wa.conversationKey,
                    source = ConversationSource.WHATSAPP_PERSONAL_PREVIEW,
                    title = wa.senderName,
                    subtitle = wa.messagePreview,
                    timestampMs = wa.timestampMs,
                    badgeText = "WA PERSONAL",
                    activeIndicator = wa.active
                )
            }
        } else emptyList()

        val businessItems = if (businessConfig.enabled) {
            businessWa.map { thread ->
                UnifiedConversationItem(
                    id = thread.phoneNumber,
                    source = ConversationSource.WHATSAPP_BUSINESS_CLOUD,
                    title = thread.contactName,
                    subtitle = thread.lastMessagePreview,
                    timestampMs = thread.lastMessageAtMs,
                    badgeText = "WA BUSINESS",
                    peerPhoneE164 = thread.phoneNumber
                )
            }
        } else emptyList()

        val filteredBySource = when (filter) {
            InboxFilter.ALL -> chatXItems + personalItems + businessItems
            InboxFilter.CHATX -> chatXItems
            InboxFilter.WHATSAPP -> personalItems + businessItems
        }

        val finalItems = filteredBySource
            .filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.subtitle.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.timestampMs }

        UiState.Success(finalItems) as UiState<List<UnifiedConversationItem>>
    }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w("InboxVM", "Error observing inbox", root)
            emit(UiState.Error(root.message ?: "Failed to load inbox"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun setFilter(filter: InboxFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _actionError.value = null
    }

    fun openDirectChat(myProfile: MemberProfile, peer: MemberProfile, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val res = chatRepo.createOrOpenDirectConversation(myProfile, peer)
            res.onSuccess(onReady)
            res.onFailure { _actionError.value = it.message ?: "Failed to start chat." }
        }
    }

    fun createGroupChat(
        myProfile: MemberProfile,
        title: String,
        peers: List<MemberProfile>,
        onReady: (String) -> Unit
    ) {
        viewModelScope.launch {
            val res = chatRepo.createGroupConversation(myProfile, title, peers)
            res.onSuccess(onReady)
            res.onFailure { _actionError.value = it.message ?: "Failed to create group." }
        }
    }
}
