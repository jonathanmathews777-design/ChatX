package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.BusinessWhatsAppConfig
import com.example.data.model.MemberProfile
import com.example.ui.components.ChatXTopBar
import com.example.ui.components.MonochromeOutlinedButton
import com.example.ui.components.MonochromePrimaryButton
import com.example.whatsapp.ChatXNotificationListenerService
import com.example.whatsapp.WhatsAppShareHelper

@Composable
fun SettingsAndSecurityScreen(
    myProfile: MemberProfile,
    darkThemeEnabled: Boolean,
    personalWaEnabled: Boolean,
    businessConfig: BusinessWhatsAppConfig,
    statusMessage: String?,
    encryptedBackupJson: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onTogglePersonalWa: (Boolean) -> Unit,
    onClearPersonalWa: () -> Unit,
    onSaveBusinessWaConfig: (Boolean, String, String, String, String) -> Unit,
    onSyncBusinessWebhook: () -> Unit,
    onClearBusinessData: () -> Unit,
    onUpdatePhone: (String) -> Unit,
    onGenerateBackup: (String) -> Unit,
    onLockSession: () -> Unit,
    onFullSignOut: () -> Unit,
    onClearMessages: () -> Unit
) {
    val context = LocalContext.current
    val hasNotificationAccess = remember(personalWaEnabled) {
        ChatXNotificationListenerService.isNotificationAccessGranted(context)
    }

    var phoneInput by remember(myProfile.phoneE164) { mutableStateOf(myProfile.phoneE164.orEmpty()) }
    var backupPassphrase by remember { mutableStateOf("") }

    var bizEnabled by remember(businessConfig.enabled) { mutableStateOf(businessConfig.enabled) }
    var bizPhoneId by remember(businessConfig.phoneNumberId) { mutableStateOf(businessConfig.phoneNumberId) }
    var bizWabaId by remember(businessConfig.wabaId) { mutableStateOf(businessConfig.wabaId) }
    var bizWebhookUrl by remember(businessConfig.webhookRelayUrl) { mutableStateOf(businessConfig.webhookRelayUrl) }
    var bizAccessToken by remember(businessConfig.accessToken) { mutableStateOf(businessConfig.accessToken) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatXTopBar(
                title = "Settings & Security",
                subtitle = "@${myProfile.username}",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (statusMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(statusMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = onClearMessages) { Text("OK") }
                        }
                    }
                }
            }

            // 1. E2EE Identity & Encrypted Backup
            item {
                SectionCard(title = "End-to-End Encryption & Backup") {
                    Text(
                        "Public Key Fingerprint (ECDH P-256):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(myProfile.keyFingerprint, style = MaterialTheme.typography.titleMedium)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("WhatsApp Deep-Link Number (optional E.164)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MonochromeOutlinedButton(
                        text = "Save Contact Number",
                        onClick = { onUpdatePhone(phoneInput) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Backup Passphrase (min 8 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MonochromePrimaryButton(
                        text = "Export AES-256-GCM Encrypted Backup",
                        enabled = backupPassphrase.length >= 8,
                        onClick = { onGenerateBackup(backupPassphrase) }
                    )
                    if (encryptedBackupJson != null) {
                        MonochromeOutlinedButton(
                            text = "Share Encrypted Backup JSON",
                            onClick = { WhatsAppShareHelper.shareTextToWhatsApp(context, encryptedBackupJson) }
                        )
                    }
                }
            }

            // 2. Personal WhatsApp Integration (NotificationListenerService)
            item {
                SectionCard(title = "Personal WhatsApp (Notification Bridge)") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Personal WhatsApp Inbox", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Reads active notification previews only. Never accesses WhatsApp private databases or sends automated replies.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = personalWaEnabled,
                            onCheckedChange = onTogglePersonalWa,
                            modifier = Modifier.testTag("toggle_personal_wa")
                        )
                    }
                    Text(
                        text = if (hasNotificationAccess) "OS Notification Access: GRANTED" else "OS Notification Access: NOT GRANTED",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasNotificationAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    MonochromeOutlinedButton(
                        text = "Open Android Notification Access Settings",
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    )
                    TextButton(onClick = onClearPersonalWa) {
                        Text("Clear Imported Notification Previews", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 3. Official WhatsApp Business Platform (Meta Cloud API)
            item {
                SectionCard(title = "WhatsApp Business (Official Cloud API)") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Business WhatsApp Inbox", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Connects to Meta Graph API v21.0 & your HTTPS webhook relay. Store permanent tokens on your backend relay.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = bizEnabled,
                            onCheckedChange = { bizEnabled = it },
                            modifier = Modifier.testTag("toggle_business_wa")
                        )
                    }
                    OutlinedTextField(
                        value = bizPhoneId,
                        onValueChange = { bizPhoneId = it },
                        label = { Text("Meta Phone Number ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bizWabaId,
                        onValueChange = { bizWabaId = it },
                        label = { Text("WhatsApp Business Account (WABA) ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bizWebhookUrl,
                        onValueChange = { bizWebhookUrl = it },
                        label = { Text("HTTPS Webhook Relay URL (Inbound)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bizAccessToken,
                        onValueChange = { bizAccessToken = it },
                        label = { Text("Cloud API Access Token (Keystore-wrapped)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MonochromePrimaryButton(
                        text = "Save Business API Config",
                        onClick = {
                            onSaveBusinessWaConfig(bizEnabled, bizPhoneId, bizWabaId, bizWebhookUrl, bizAccessToken)
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MonochromeOutlinedButton(
                            text = "Sync Webhook Relay",
                            enabled = bizEnabled && !busy,
                            modifier = Modifier.weight(1f),
                            onClick = onSyncBusinessWebhook
                        )
                    }
                    TextButton(onClick = onClearBusinessData) {
                        Text("Clear Business WhatsApp Local Cache", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 4. Interface & Stage Audit Status
            item {
                SectionCard(title = "Appearance & Stage Audit Status") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Obsidian Dark Mode (Default)", style = MaterialTheme.typography.titleMedium)
                        Switch(checked = darkThemeEnabled, onCheckedChange = onToggleDarkTheme)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("Implemented Now (Stage 1 + WhatsApp):", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "• Invite-only PBKDF2-SHA256 username/password gate & sole Super Admin console\n" +
                            "• Real-time Firestore 1-to-1 & Group sync with zero-trust security rules\n" +
                            "• ECDH P-256 + HKDF-SHA256 + AES-256-GCM encryption & AndroidKeyStore wrapping\n" +
                            "• Disappearing timers, edit/delete, read receipts, typing indicators, voice notes\n" +
                            "• Native WhatsApp sharing, Personal WhatsApp NotificationListenerService, Official Meta Cloud API client",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Not Yet Deployed / Requires External Audit:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "• External security audit of multi-device key rotation & Signal Double Ratchet upgrade\n" +
                            "• Stage 2 WebRTC signaling & TURN/STUN server for live remote audio/video calls\n" +
                            "• Stage 2 encrypted remote media blob bucket (Firebase Storage / S3) for cross-device file binaries\n" +
                            "• External HTTPS webhook server for Meta WhatsApp Business inbound webhooks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. Session Controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MonochromeOutlinedButton(
                        text = "Lock ChatX Session (Require Username & Password)",
                        onClick = onLockSession
                    )
                    MonochromeOutlinedButton(
                        text = "Disconnect Transport Account (Full Sign Out)",
                        onClick = onFullSignOut
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
