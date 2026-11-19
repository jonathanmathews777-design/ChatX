package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Invitation
import com.example.data.model.MemberProfile
import com.example.data.model.MemberRole
import com.example.data.model.MemberStatus
import com.example.data.model.UiState
import com.example.ui.components.ChatXTopBar
import com.example.ui.components.MonochromePrimaryButton
import com.example.viewmodel.AdminDashboardData
import com.example.whatsapp.WhatsAppShareHelper

@Composable
fun AdminConsoleScreen(
    myProfile: MemberProfile,
    dashboardState: UiState<AdminDashboardData>,
    busy: Boolean,
    lastGeneratedCode: String?,
    actionError: String?,
    onBack: () -> Unit,
    onGenerateInvite: (label: String, autoApprove: Boolean, validHours: Long) -> Unit,
    onRevokeInvite: (String) -> Unit,
    onApproveMember: (String) -> Unit,
    onSuspendMember: (String) -> Unit,
    onRemoveMember: (MemberProfile) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    var inviteLabel by remember { mutableStateOf("") }
    var autoApprove by remember { mutableStateOf(true) }
    var validHours by remember { mutableLongStateOf(72L) }
    var memberPendingRemoval by remember { mutableStateOf<MemberProfile?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatXTopBar(
                title = "Super Admin Console",
                subtitle = "@${myProfile.username} • Sole Administrator",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        when (dashboardState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(dashboardState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val data = dashboardState.data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null)
                                    Text("Generate Single-Use Invite", style = MaterialTheme.typography.titleLarge)
                                }
                                OutlinedTextField(
                                    value = inviteLabel,
                                    onValueChange = { inviteLabel = it },
                                    label = { Text("Recipient Note (optional)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_invite_label_input")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Auto-approve upon redemption", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (autoApprove) "Member enters ACTIVE immediately" else "Member waits in PENDING queue",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(checked = autoApprove, onCheckedChange = { autoApprove = it })
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(24L to "24h", 72L to "72h", 168L to "7d").forEach { (hrs, label) ->
                                        FilterChip(
                                            selected = validHours == hrs,
                                            onClick = { validHours = hrs },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                                if (actionError != null) {
                                    Text(actionError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                MonochromePrimaryButton(
                                    text = "Generate Single-Use Code",
                                    loading = busy,
                                    modifier = Modifier.testTag("generate_invite_button"),
                                    onClick = {
                                        onGenerateInvite(inviteLabel, autoApprove, validHours)
                                        inviteLabel = ""
                                    }
                                )
                                if (lastGeneratedCode != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("NEW SINGLE-USE CODE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(lastGeneratedCode, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            }
                                            Row {
                                                IconButton(onClick = {
                                                    copyToClipboard(context, lastGeneratedCode)
                                                }) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy invite code")
                                                }
                                                IconButton(onClick = {
                                                    WhatsAppShareHelper.shareTextToWhatsApp(
                                                        context,
                                                        "Your private single-use ChatX invitation code: $lastGeneratedCode"
                                                    )
                                                }) {
                                                    Icon(Icons.Default.Share, contentDescription = "Share code via WhatsApp")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "MEMBERS (${data.members.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(data.members, key = { it.uid }) { member ->
                        MemberAdminCard(
                            member = member,
                            isSelf = member.uid == myProfile.uid,
                            onApprove = { onApproveMember(member.uid) },
                            onSuspend = { onSuspendMember(member.uid) },
                            onRemove = { memberPendingRemoval = member }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "INVITATION CODES (${data.invitations.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(data.invitations, key = { it.codeId }) { invite ->
                        InvitationCard(
                            invite = invite,
                            onCopy = { copyToClipboard(context, invite.codeId) },
                            onRevoke = { onRevokeInvite(invite.codeId) }
                        )
                    }
                }
            }
        }
    }

    memberPendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { memberPendingRemoval = null },
            title = { Text("Remove @${target.username}?") },
            text = { Text("This permanently deletes their ChatX member record and revokes workspace access.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveMember(target)
                        memberPendingRemoval = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingRemoval = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MemberAdminCard(
    member: MemberProfile,
    isSelf: Boolean,
    onApprove: () -> Unit,
    onSuspend: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "@${member.username} • ${member.role}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (member.parsedStatus) {
                        MemberStatus.ACTIVE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        MemberStatus.SUSPENDED -> MaterialTheme.colorScheme.errorContainer
                        MemberStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = member.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (member.parsedStatus == MemberStatus.SUSPENDED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (!isSelf && member.parsedRole != MemberRole.SUPER_ADMIN) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (member.parsedStatus != MemberStatus.ACTIVE) {
                        OutlinedButton(onClick = onApprove, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Approve")
                        }
                    } else {
                        OutlinedButton(onClick = onSuspend, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Suspend")
                        }
                    }
                    OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(4.dp))
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun InvitationCard(
    invite: Invitation,
    onCopy: () -> Unit,
    onRevoke: () -> Unit
) {
    val stateLabel = when {
        invite.used -> "REDEEMED BY @${invite.usedByUsername ?: "member"}"
        invite.revoked -> "REVOKED"
        else -> "ACTIVE • SINGLE-USE"
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(invite.codeId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (invite.label.isNotBlank()) {
                    Text(invite.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!invite.used && !invite.revoked) {
                Row {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy code")
                    }
                    TextButton(onClick = onRevoke) {
                        Text("Revoke", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ChatX Invite Code", text))
    Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT).show()
}
