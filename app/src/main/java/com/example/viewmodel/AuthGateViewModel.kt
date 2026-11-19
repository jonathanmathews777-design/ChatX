package com.example.viewmodel

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.model.MemberProfile
import com.example.data.model.MemberStatus
import com.example.data.repository.AuthAndAdminRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ChatXAccessState {
    data object Loading : ChatXAccessState
    data object SuperAdminBootstrap : ChatXAccessState
    data class LockedCredentialsRequired(val boundUsername: String?) : ChatXAccessState
    data class PendingAdminApproval(val profile: MemberProfile) : ChatXAccessState
    data class Suspended(val profile: MemberProfile) : ChatXAccessState
    data class Unlocked(val profile: MemberProfile) : ChatXAccessState
    data class Error(val message: String) : ChatXAccessState
}

class AuthGateViewModel(
    private val authRepo: AuthAndAdminRepository,
    private val prefs: PreferencesManager,
    private val auth: FirebaseAuth,
    private val currentUserId: String
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val accessState: StateFlow<ChatXAccessState> = combine(
        authRepo.observeMyProfile(currentUserId),
        prefs.observeSessionUnlocked(currentUserId)
    ) { profile, unlockedUsername ->
        val user = auth.currentUser
        val isSuperAdminEmail = authRepo.isSuperAdminEmail(user?.email, user?.isEmailVerified == true)
        when {
            profile == null && isSuperAdminEmail -> ChatXAccessState.SuperAdminBootstrap
            profile == null -> ChatXAccessState.LockedCredentialsRequired(boundUsername = null)
            profile.parsedStatus == MemberStatus.PENDING -> ChatXAccessState.PendingAdminApproval(profile)
            profile.parsedStatus == MemberStatus.SUSPENDED -> ChatXAccessState.Suspended(profile)
            unlockedUsername != null && unlockedUsername == profile.username -> ChatXAccessState.Unlocked(profile)
            else -> ChatXAccessState.LockedCredentialsRequired(boundUsername = profile.username)
        }
    }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w("AuthGateVM", "Error observing access state", root)
            emit(ChatXAccessState.Error(root.message ?: "Failed to load membership status"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ChatXAccessState.Loading
        )

    fun clearError() {
        _actionError.value = null
    }

    fun bootstrapSuperAdmin(username: String, displayName: String, password: String) {
        viewModelScope.launch {
            _busy.value = true
            _actionError.value = null
            val res = authRepo.bootstrapSuperAdmin(username, displayName, password)
            _busy.value = false
            res.onFailure { _actionError.value = it.message ?: "Failed to initialize Super Admin." }
        }
    }

    fun signInWithUsernamePassword(username: String, password: String) {
        viewModelScope.launch {
            _busy.value = true
            _actionError.value = null
            val res = authRepo.loginWithUsernamePassword(username, password)
            _busy.value = false
            res.onFailure { _actionError.value = it.message ?: "Invalid username or password." }
        }
    }

    fun redeemInvite(inviteCode: String, username: String, displayName: String, password: String) {
        viewModelScope.launch {
            _busy.value = true
            _actionError.value = null
            val res = authRepo.redeemInviteAndRegister(inviteCode, username, displayName, password)
            _busy.value = false
            res.onFailure { _actionError.value = it.message ?: "Unable to redeem invitation code." }
        }
    }

    fun lockChatXSession() {
        viewModelScope.launch {
            authRepo.lockSession()
        }
    }

    fun fullSignOut(credentialManager: CredentialManager, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    authRepo.lockSession()
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("AuthGateVM", "Sign-out credential cleanup warning: ${e.message}")
                } finally {
                    auth.signOut()
                    onDone()
                }
            }
        }
    }
}
