package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ConversationSource
import com.example.data.model.InboxFilter
import com.example.data.model.MemberProfile
import com.example.data.model.MemberRole
import com.example.data.model.UiState
import com.example.data.model.UnifiedConversationItem
import com.example.ui.components.ChatXAvatar
import com.example.ui.components.MonochromePrimaryButton
import com.example.ui.components.SourcePill
import com.example.ui.components.formatShortTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedInboxScreen(
    myProfile: MemberProfile,
    conversationsState: UiState<List<UnifiedConversationItem>>,
    activeMembers: List<MemberProfile>,
    selectedFilter: InboxFilter,
    searchQuery: String,
    businessWaEnabled: Boolean,
    onSelectFilter: (InboxFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenConversation: (UnifiedConversationItem) -> Unit,
    onStartDirectChat: (MemberProfile) -> Unit,
    onCreateGroupChat: (title: String, peers: List<MemberProfile>) -> Unit,
    onStartBusinessChat: (phone: String, name: String) -> Unit,
    onOpenAdminConsole: () -> Unit,
    onOpenSettings: () -> Unit,
    onLockSession: () -> Unit
) {
    var showComposeSheet by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ChatX", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "@${myProfile.username} • ${if (myProfile.parsedRole == MemberRole.SUPER_ADMIN) "SUPER ADMIN" else "E2EE VERIFIED"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (myProfile.parsedRole == MemberRole.SUPER_ADMIN) {
                        IconButton(
                            onClick = onOpenAdminConsole,
                            modifier = Modifier.testTag("open_admin_console_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Super Admin Console"
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings and Integrations"
                        )
                    }
                    IconButton(
                        onClick = onLockSession,
                        modifier = Modifier.testTag("lock_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock ChatX Session"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showComposeSheet = true },
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("new_chat_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Conversation")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search & Unified Inbox Source Filters (All / ChatX / WhatsApp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search messages or contacts") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inbox_search_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InboxFilterChip(
                        label = stringResource(R.string.inbox_filter_all),
                        selected = selectedFilter == InboxFilter.ALL,
                        tag = "filter_all",
                        onClick = { onSelectFilter(InboxFilter.ALL) }
                    )
                    InboxFilterChip(
                        label = stringResource(R.string.inbox_filter_chatx),
                        selected = selectedFilter == InboxFilter.CHATX,
                        tag = "filter_chatx",
                        onClick = { onSelectFilter(InboxFilter.CHATX) }
                    )
                    InboxFilterChip(
                        label = stringResource(R.string.inbox_filter_whatsapp),
                        selected = selectedFilter == InboxFilter.WHATSAPP,
                        tag = "filter_whatsapp",
                        onClick = { onSelectFilter(InboxFilter.WHATSAPP) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            when (conversationsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = conversationsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is UiState.Success -> {
                    val items = conversationsState.data
                    if (items.isEmpty()) {
                        EmptyInboxView(
                            selectedFilter = selectedFilter,
                            onNewChat = { showComposeSheet = true },
                            onOpenSettings = onOpenSettings
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(items, key = { "${it.source.name}_${it.id}" }) { item ->
                                UnifiedConversationRow(
                                    item = item,
                                    onClick = { onOpenConversation(item) }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(start = 76.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showComposeSheet) {
        NewConversationSheet(
            myProfile = myProfile,
            activeMembers = activeMembers.filter { it.uid != myProfile.uid },
            businessWaEnabled = businessWaEnabled,
            onDismiss = { showComposeSheet = false },
            onSelectPeer = { peer ->
                showComposeSheet = false
                onStartDirectChat(peer)
            },
            onCreateGroup = { title, peers ->
                showComposeSheet = false
                onCreateGroupChat(title, peers)
            },
            onStartBusinessThread = { phone, name ->
                showComposeSheet = false
                onStartBusinessChat(phone, name)
            }
        )
    }
}

@Composable
private fun InboxFilterChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = Modifier.testTag(tag),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun UnifiedConversationRow(
    item: UnifiedConversationItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("conversation_row_${item.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatXAvatar(label = item.title, online = item.online, size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatShortTime(item.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.activeIndicator) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SourcePill(source = item.source, labelOverride = item.badgeText)
                if (item.disappearingSeconds > 0L) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Disappearing messages enabled",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyInboxView(
    selectedFilter: InboxFilter,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (selectedFilter) {
                    InboxFilter.ALL -> "No active conversations yet"
                    InboxFilter.CHATX -> "No ChatX E2EE threads yet"
                    InboxFilter.WHATSAPP -> "No WhatsApp threads imported"
                },
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = when (selectedFilter) {
                    InboxFilter.WHATSAPP -> "Enable Personal WhatsApp Notification Previews or connect your official WhatsApp Business Cloud API in Settings."
                    else -> "Tap + to start an end-to-end encrypted 1-to-1 or group chat with an invited ChatX member."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (selectedFilter == InboxFilter.WHATSAPP) {
                MonochromePrimaryButton(text = "Configure WhatsApp Integrations", onClick = onOpenSettings)
            } else {
                MonochromePrimaryButton(text = "Start Encrypted Conversation", onClick = onNewChat)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewConversationSheet(
    myProfile: MemberProfile,
    activeMembers: List<MemberProfile>,
    businessWaEnabled: Boolean,
    onDismiss: () -> Unit,
    onSelectPeer: (MemberProfile) -> Unit,
    onCreateGroup: (title: String, peers: List<MemberProfile>) -> Unit,
    onStartBusinessThread: (phone: String, name: String) -> Unit
) {
    var mode by remember { mutableStateOf("DIRECT") } // DIRECT, GROUP, WA_BUSINESS
    var groupTitle by remember { mutableStateOf("") }
    val selectedPeers = remember { mutableStateListOf<MemberProfile>() }
    var waPhone by remember { mutableStateOf("") }
    var waName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("New Conversation", style = MaterialTheme.typography.headlineSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == "DIRECT",
                    onClick = { mode = "DIRECT" },
                    label = { Text("1-to-1 E2EE") }
                )
                FilterChip(
                    selected = mode == "GROUP",
                    onClick = { mode = "GROUP" },
                    label = { Text("Group E2EE") },
                    leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                if (businessWaEnabled) {
                    FilterChip(
                        selected = mode == "WA_BUSINESS",
                        onClick = { mode = "WA_BUSINESS" },
                        label = { Text("WA Business") }
                    )
                }
            }

            if (mode == "WA_BUSINESS") {
                OutlinedTextField(
                    value = waPhone,
                    onValueChange = { waPhone = it },
                    label = { Text("Recipient Phone (E.164 digits, e.g. 15551234567)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = waName,
                    onValueChange = { waName = it },
                    label = { Text("Customer / Contact Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MonochromePrimaryButton(
                    text = "Open Business WhatsApp Thread",
                    enabled = waPhone.filter { it.isDigit() }.length >= 7,
                    onClick = { onStartBusinessThread(waPhone.filter { it.isDigit() }, waName.ifBlank { "+${waPhone.filter { it.isDigit() }}" }) }
                )
            } else {
                if (mode == "GROUP") {
                    OutlinedTextField(
                        value = groupTitle,
                        onValueChange = { groupTitle = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (activeMembers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = "No other active members yet. ${if (myProfile.parsedRole == MemberRole.SUPER_ADMIN) "Generate an invite code in the Admin Console to onboard members." else "Ask the Super Admin to invite additional members."}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    activeMembers.forEach { peer ->
                        val checked = selectedPeers.any { it.uid == peer.uid }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (mode == "DIRECT") {
                                        onSelectPeer(peer)
                                    } else {
                                        if (checked) selectedPeers.removeAll { it.uid == peer.uid }
                                        else selectedPeers.add(peer)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatXAvatar(label = peer.displayName, online = peer.online, size = 40.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "@${peer.username} • Key ${peer.keyFingerprint.take(9)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (mode == "GROUP") {
                                Checkbox(checked = checked, onCheckedChange = null)
                            }
                        }
                    }
                    if (mode == "GROUP") {
                        MonochromePrimaryButton(
                            text = "Create Encrypted Group (${selectedPeers.size})",
                            enabled = groupTitle.isNotBlank() && selectedPeers.isNotEmpty(),
                            onClick = { onCreateGroup(groupTitle, selectedPeers.toList()) }
                        )
                    }
                }
            }
        }
    }
}
