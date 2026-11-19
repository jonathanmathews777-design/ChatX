package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ChatXApplication
import com.example.data.model.ConversationSource
import com.example.data.model.MemberProfile
import com.example.ui.screens.AdminConsoleScreen
import com.example.ui.screens.BusinessWhatsAppDetailScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatXLoginAndInviteScreen
import com.example.ui.screens.MembershipStatusLockScreen
import com.example.ui.screens.PersonalWhatsAppDetailScreen
import com.example.ui.screens.SettingsAndSecurityScreen
import com.example.ui.screens.SuperAdminBootstrapScreen
import com.example.ui.screens.TransportConnectScreen
import com.example.ui.screens.UnifiedInboxScreen
import com.example.ui.screens.attemptAutoSignIn
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.AuthGateViewModel
import com.example.viewmodel.ChatDetailViewModel
import com.example.viewmodel.ChatXAccessState
import com.example.viewmodel.InboxViewModel
import com.example.viewmodel.WhatsAppAndSettingsViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object ChatXRoutes {
    const val INBOX = "inbox"
    const val CHAT_DETAIL = "chat/{conversationId}"
    const val WA_PERSONAL = "wa_personal/{conversationKey}"
    const val WA_BUSINESS = "wa_business/{phoneNumber}"
    const val ADMIN = "admin"
    const val SETTINGS = "settings"

    fun chatDetail(id: String) = "chat/$id"
    fun waPersonal(key: String) = "wa_personal/$key"
    fun waBusiness(phone: String) = "wa_business/$phone"
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun ChatXAppRoot(
    app: ChatXApplication,
    settingsVm: WhatsAppAndSettingsViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by app.firebaseAuth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = app.firebaseAuth.currentUser)
    var attemptedSilentSignIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (currentUser == null && !attemptedSilentSignIn) {
            attemptedSilentSignIn = true
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        TransportConnectScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedChatXGate(
                app = app,
                currentUserId = user.uid,
                settingsVm = settingsVm,
                credentialManager = credentialManager
            )
        }
    }
}

@Composable
private fun AuthenticatedChatXGate(
    app: ChatXApplication,
    currentUserId: String,
    settingsVm: WhatsAppAndSettingsViewModel,
    credentialManager: CredentialManager
) {
    val authGateVm: AuthGateViewModel = viewModel(
        key = "auth_gate_$currentUserId",
        factory = viewModelFactory {
            initializer {
                AuthGateViewModel(
                    authRepo = app.authAndAdminRepository,
                    prefs = app.preferencesManager,
                    auth = app.firebaseAuth,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val accessState by authGateVm.accessState.collectAsStateWithLifecycle()
    val busy by authGateVm.busy.collectAsStateWithLifecycle()
    val actionError by authGateVm.actionError.collectAsStateWithLifecycle()

    when (val state = accessState) {
        is ChatXAccessState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is ChatXAccessState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is ChatXAccessState.SuperAdminBootstrap -> {
            SuperAdminBootstrapScreen(
                busy = busy,
                error = actionError,
                onBootstrap = { username, displayName, password ->
                    authGateVm.bootstrapSuperAdmin(username, displayName, password)
                },
                onDisconnectTransport = { authGateVm.fullSignOut(credentialManager) }
            )
        }
        is ChatXAccessState.LockedCredentialsRequired -> {
            ChatXLoginAndInviteScreen(
                boundUsername = state.boundUsername,
                busy = busy,
                error = actionError,
                onClearError = { authGateVm.clearError() },
                onSignIn = { username, password ->
                    authGateVm.signInWithUsernamePassword(username, password)
                },
                onRedeemInvite = { code, username, displayName, password ->
                    authGateVm.redeemInvite(code, username, displayName, password)
                },
                onDisconnectTransport = { authGateVm.fullSignOut(credentialManager) }
            )
        }
        is ChatXAccessState.PendingAdminApproval -> {
            MembershipStatusLockScreen(
                profile = state.profile,
                isSuspended = false,
                onDisconnectTransport = { authGateVm.fullSignOut(credentialManager) }
            )
        }
        is ChatXAccessState.Suspended -> {
            MembershipStatusLockScreen(
                profile = state.profile,
                isSuspended = true,
                onDisconnectTransport = { authGateVm.fullSignOut(credentialManager) }
            )
        }
        is ChatXAccessState.Unlocked -> {
            UnlockedChatXWorkspace(
                app = app,
                myProfile = state.profile,
                currentUserId = currentUserId,
                authGateVm = authGateVm,
                settingsVm = settingsVm,
                credentialManager = credentialManager
            )
        }
    }
}

@Composable
private fun UnlockedChatXWorkspace(
    app: ChatXApplication,
    myProfile: MemberProfile,
    currentUserId: String,
    authGateVm: AuthGateViewModel,
    settingsVm: WhatsAppAndSettingsViewModel,
    credentialManager: CredentialManager
) {
    val navController = rememberNavController()
    val inboxVm: InboxViewModel = viewModel(
        key = "inbox_$currentUserId",
        factory = viewModelFactory {
            initializer {
                InboxViewModel(
                    chatRepo = app.chatRepository,
                    authRepo = app.authAndAdminRepository,
                    waNotificationDao = app.localDb.whatsAppNotificationDao(),
                    waBusinessDao = app.localDb.whatsAppBusinessDao(),
                    prefs = app.preferencesManager,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val conversationsState by inboxVm.unifiedConversations.collectAsStateWithLifecycle()
    val activeMembers by inboxVm.activeMembers.collectAsStateWithLifecycle()
    val selectedFilter by inboxVm.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by inboxVm.searchQuery.collectAsStateWithLifecycle()
    val darkThemeEnabled by settingsVm.darkThemeEnabled.collectAsStateWithLifecycle()
    val personalWaEnabled by settingsVm.personalWaEnabled.collectAsStateWithLifecycle()
    val businessConfig by settingsVm.businessWaConfig.collectAsStateWithLifecycle()
    val settingsStatus by settingsVm.statusMessage.collectAsStateWithLifecycle()
    val encryptedBackup by settingsVm.encryptedBackupJson.collectAsStateWithLifecycle()
    val settingsBusy by settingsVm.busy.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = ChatXRoutes.INBOX
    ) {
        composable(ChatXRoutes.INBOX) {
            UnifiedInboxScreen(
                myProfile = myProfile,
                conversationsState = conversationsState,
                activeMembers = activeMembers,
                selectedFilter = selectedFilter,
                searchQuery = searchQuery,
                businessWaEnabled = businessConfig.enabled,
                onSelectFilter = { inboxVm.setFilter(it) },
                onSearchChange = { inboxVm.setSearchQuery(it) },
                onOpenConversation = { item ->
                    when (item.source) {
                        ConversationSource.CHATX_E2EE -> navController.navigate(ChatXRoutes.chatDetail(item.id))
                        ConversationSource.WHATSAPP_PERSONAL_PREVIEW -> navController.navigate(ChatXRoutes.waPersonal(item.id))
                        ConversationSource.WHATSAPP_BUSINESS_CLOUD -> navController.navigate(ChatXRoutes.waBusiness(item.id))
                    }
                },
                onStartDirectChat = { peer ->
                    inboxVm.openDirectChat(myProfile, peer) { convId ->
                        navController.navigate(ChatXRoutes.chatDetail(convId))
                    }
                },
                onCreateGroupChat = { title, peers ->
                    inboxVm.createGroupChat(myProfile, title, peers) { convId ->
                        navController.navigate(ChatXRoutes.chatDetail(convId))
                    }
                },
                onStartBusinessChat = { phone, _ ->
                    navController.navigate(ChatXRoutes.waBusiness(phone))
                },
                onOpenAdminConsole = { navController.navigate(ChatXRoutes.ADMIN) },
                onOpenSettings = { navController.navigate(ChatXRoutes.SETTINGS) },
                onLockSession = { authGateVm.lockChatXSession() }
            )
        }

        composable(
            route = ChatXRoutes.CHAT_DETAIL,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { entry ->
            val conversationId = entry.arguments?.getString("conversationId") ?: return@composable
            val chatVm: ChatDetailViewModel = viewModel(
                key = "chat_${currentUserId}_$conversationId",
                factory = viewModelFactory {
                    initializer {
                        ChatDetailViewModel(
                            chatRepo = app.chatRepository,
                            authRepo = app.authAndAdminRepository,
                            conversationId = conversationId,
                            currentUserId = currentUserId
                        )
                    }
                }
            )
            val uiState by chatVm.uiState.collectAsStateWithLifecycle()
            val sending by chatVm.sending.collectAsStateWithLifecycle()
            val actionError by chatVm.actionError.collectAsStateWithLifecycle()

            ChatDetailScreen(
                myProfile = myProfile,
                uiState = uiState,
                sending = sending,
                actionError = actionError,
                onClearError = { chatVm.clearError() },
                onBack = { navController.popBackStack() },
                onSendMessage = { plaintext, type, attName, attMime, attSize, voiceMs ->
                    chatVm.sendMessage(myProfile, plaintext, type, attName, attMime, attSize, voiceMs)
                },
                onEditMessage = { msg, newPlain -> chatVm.editMessage(msg, newPlain) },
                onDeleteMessage = { msg -> chatVm.deleteMessage(msg) },
                onSetTyping = { chatVm.setTyping(it) },
                onSetDisappearingTimer = { chatVm.setDisappearingTimer(it) }
            )
        }

        composable(
            route = ChatXRoutes.WA_PERSONAL,
            arguments = listOf(navArgument("conversationKey") { type = NavType.StringType })
        ) { entry ->
            val key = entry.arguments?.getString("conversationKey") ?: return@composable
            val previews by remember(key) {
                settingsVm.observePersonalThread(key)
            }.collectAsStateWithLifecycle(initialValue = emptyList())

            PersonalWhatsAppDetailScreen(
                conversationKey = key,
                previews = previews,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ChatXRoutes.WA_BUSINESS,
            arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType })
        ) { entry ->
            val phone = entry.arguments?.getString("phoneNumber") ?: return@composable
            val thread by remember(phone) {
                settingsVm.observeBusinessThread(phone)
            }.collectAsStateWithLifecycle(initialValue = null)
            val messages by remember(phone) {
                settingsVm.observeBusinessMessages(phone)
            }.collectAsStateWithLifecycle(initialValue = emptyList())

            BusinessWhatsAppDetailScreen(
                phoneNumber = phone,
                thread = thread,
                messages = messages,
                windowOpen = settingsVm.isBusinessWindowOpen(thread),
                busy = settingsBusy,
                statusMessage = settingsStatus,
                onBack = { navController.popBackStack() },
                onSendReply = { body -> settingsVm.sendBusinessReply(phone, thread?.contactName ?: "+$phone", body) },
                onSendTemplate = { tpl, lang -> settingsVm.sendBusinessTemplate(phone, thread?.contactName ?: "+$phone", tpl, lang) },
                onSendMedia = { type, url, cap -> settingsVm.sendBusinessMedia(phone, thread?.contactName ?: "+$phone", type, url, cap) },
                onSyncWebhook = { settingsVm.syncBusinessWebhook() },
                onClearStatus = { settingsVm.clearMessages() }
            )
        }

        composable(ChatXRoutes.ADMIN) {
            val adminVm: AdminViewModel = viewModel(
                key = "admin_$currentUserId",
                factory = viewModelFactory {
                    initializer {
                        AdminViewModel(app.authAndAdminRepository, currentUserId)
                    }
                }
            )
            val dashboardState by adminVm.dashboardState.collectAsStateWithLifecycle()
            val busy by adminVm.busy.collectAsStateWithLifecycle()
            val lastCode by adminVm.lastGeneratedCode.collectAsStateWithLifecycle()
            val error by adminVm.actionError.collectAsStateWithLifecycle()

            AdminConsoleScreen(
                myProfile = myProfile,
                dashboardState = dashboardState,
                busy = busy,
                lastGeneratedCode = lastCode,
                actionError = error,
                onBack = { navController.popBackStack() },
                onGenerateInvite = { label, autoApprove, hrs -> adminVm.generateInvite(label, autoApprove, hrs) },
                onRevokeInvite = { adminVm.revokeInvite(it) },
                onApproveMember = { adminVm.approveMember(it) },
                onSuspendMember = { adminVm.suspendMember(it) },
                onRemoveMember = { adminVm.removeMember(it) },
                onClearFeedback = { adminVm.clearFeedback() }
            )
        }

        composable(ChatXRoutes.SETTINGS) {
            SettingsAndSecurityScreen(
                myProfile = myProfile,
                darkThemeEnabled = darkThemeEnabled,
                personalWaEnabled = personalWaEnabled,
                businessConfig = businessConfig,
                statusMessage = settingsStatus,
                encryptedBackupJson = encryptedBackup,
                busy = settingsBusy,
                onBack = { navController.popBackStack() },
                onToggleDarkTheme = { settingsVm.setDarkTheme(it) },
                onTogglePersonalWa = { settingsVm.setPersonalWaEnabled(it) },
                onClearPersonalWa = { settingsVm.clearPersonalWaPreviews() },
                onSaveBusinessWaConfig = { en, pid, wid, url, tok ->
                    settingsVm.saveBusinessConfig(en, pid, wid, url, tok)
                },
                onSyncBusinessWebhook = { settingsVm.syncBusinessWebhook() },
                onClearBusinessData = { settingsVm.clearBusinessData() },
                onUpdatePhone = { settingsVm.updateMyPhone(it) },
                onGenerateBackup = { pass ->
                    settingsVm.generateEncryptedBackup(myProfile.username, myProfile.keyFingerprint, pass)
                },
                onLockSession = { authGateVm.lockChatXSession() },
                onFullSignOut = { authGateVm.fullSignOut(credentialManager) },
                onClearMessages = { settingsVm.clearMessages() }
            )
        }
    }
}
