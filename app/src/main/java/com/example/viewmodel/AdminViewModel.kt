package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Invitation
import com.example.data.model.MemberProfile
import com.example.data.model.MemberStatus
import com.example.data.model.UiState
import com.example.data.repository.AuthAndAdminRepository
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

data class AdminDashboardData(
    val invitations: List<Invitation>,
    val members: List<MemberProfile>
)

class AdminViewModel(
    private val authRepo: AuthAndAdminRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastGeneratedCode = MutableStateFlow<String?>(null)
    val lastGeneratedCode: StateFlow<String?> = _lastGeneratedCode.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val dashboardState: StateFlow<UiState<AdminDashboardData>> = combine(
        authRepo.observeInvitationsForAdmin(currentUserId),
        authRepo.observeAllMembersForAdmin(currentUserId)
    ) { invites, members ->
        UiState.Success(AdminDashboardData(invites, members)) as UiState<AdminDashboardData>
    }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w("AdminVM", "Error observing admin dashboard", root)
            emit(UiState.Error(root.message ?: "Admin access verification failed"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun clearFeedback() {
        _actionError.value = null
        _lastGeneratedCode.value = null
    }

    fun generateInvite(label: String, autoApprove: Boolean, validHours: Long) {
        viewModelScope.launch {
            _busy.value = true
            _actionError.value = null
            val res = authRepo.createInvitation(label, autoApprove, validHours)
            _busy.value = false
            res.onSuccess { _lastGeneratedCode.value = it }
            res.onFailure { _actionError.value = it.message ?: "Failed to create invitation code." }
        }
    }

    fun revokeInvite(codeId: String) {
        viewModelScope.launch {
            val res = authRepo.revokeInvitation(codeId)
            res.onFailure { _actionError.value = it.message ?: "Failed to revoke invite." }
        }
    }

    fun approveMember(uid: String) {
        viewModelScope.launch {
            val res = authRepo.updateMemberStatus(uid, MemberStatus.ACTIVE)
            res.onFailure { _actionError.value = it.message ?: "Failed to approve member." }
        }
    }

    fun suspendMember(uid: String) {
        viewModelScope.launch {
            val res = authRepo.updateMemberStatus(uid, MemberStatus.SUSPENDED)
            res.onFailure { _actionError.value = it.message ?: "Failed to suspend member." }
        }
    }

    fun removeMember(member: MemberProfile) {
        viewModelScope.launch {
            val res = authRepo.removeMember(member)
            res.onFailure { _actionError.value = it.message ?: "Failed to remove member." }
        }
    }
}
