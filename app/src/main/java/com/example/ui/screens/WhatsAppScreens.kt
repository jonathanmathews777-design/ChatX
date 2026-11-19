package com.example.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.WhatsAppBusinessMessageEntity
import com.example.data.local.WhatsAppBusinessThreadEntity
import com.example.data.local.WhatsAppNotificationEntity
import com.example.data.model.ConversationSource
import com.example.ui.components.ChatXTopBar
import com.example.ui.components.MonochromeOutlinedButton
import com.example.ui.components.SourcePill
import com.example.ui.components.formatShortTime
import com.example.whatsapp.WhatsAppShareHelper

@Composable
fun PersonalWhatsAppDetailScreen(
    conversationKey: String,
    previews: List<WhatsAppNotificationEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val senderName = previews.lastOrNull()?.senderName ?: conversationKey

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatXTopBar(
                title = senderName,
                subtitle = "WhatsApp • Notification Previews Only",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        val launch = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                        if (launch != null) context.startActivity(launch)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open WhatsApp App")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SourcePill(source = ConversationSource.WHATSAPP_PERSONAL_PREVIEW)
                        Text(
                            text = stringResource(R.string.wa_personal_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (previews.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No captured previews for this thread.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(previews, key = { it.notificationKey }) { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.senderName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        formatShortTime(item.timestampMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(item.messagePreview, style = MaterialTheme.typography.bodyLarge)
                                if (!item.active) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.NotificationsOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Notification dismissed in system shade",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(14.dp)
            ) {
                MonochromeOutlinedButton(
                    text = "Reply directly inside WhatsApp",
                    onClick = {
                        val launch = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                        if (launch != null) context.startActivity(launch)
                        else WhatsAppShareHelper.shareTextToWhatsApp(context, "")
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessWhatsAppDetailScreen(
    phoneNumber: String,
    thread: WhatsAppBusinessThreadEntity?,
    messages: List<WhatsAppBusinessMessageEntity>,
    windowOpen: Boolean,
    busy: Boolean,
    statusMessage: String?,
    onBack: () -> Unit,
    onSendReply: (String) -> Unit,
    onSendTemplate: (templateName: String, languageCode: String) -> Unit,
    onSendMedia: (mediaType: String, mediaUrl: String, caption: String?) -> Unit,
    onSyncWebhook: () -> Unit,
    onClearStatus: () -> Unit
) {
    val contactName = thread?.contactName ?: "+$phoneNumber"
    var draft by remember { mutableStateOf("") }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }

    var templateName by remember { mutableStateOf("hello_world") }
    var templateLang by remember { mutableStateOf("en_US") }
    var mediaType by remember { mutableStateOf("image") }
    var mediaUrl by remember { mutableStateOf("") }
    var mediaCaption by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatXTopBar(
                title = contactName,
                subtitle = "+$phoneNumber • Official Meta Cloud API",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showTemplateDialog = true }) {
                        Icon(Icons.Default.ViewAgenda, contentDescription = "Send Approved Template")
                    }
                    IconButton(onClick = onSyncWebhook, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync Webhook Events")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
        ) {
            Surface(
                color = if (windowOpen) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SourcePill(source = ConversationSource.WHATSAPP_BUSINESS_CLOUD)
                    Text(
                        text = if (windowOpen) {
                            "24h customer service window OPEN • Free-form & media replies permitted"
                        } else {
                            stringResource(R.string.wa_business_window_closed)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (windowOpen) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (statusMessage != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(statusMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearStatus) { Text("OK") }
                    }
                }
            }

            if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Official WhatsApp Business Thread", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Send an approved Meta template message to initiate outreach, or sync inbound customer messages from your webhook relay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { showTemplateDialog = true }) {
                            Text("Send Approved Template")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isOut = msg.direction == "OUTBOUND"
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isOut) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isOut) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (isOut) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(msg.body, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${msg.messageType} • ${formatShortTime(msg.timestampMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOut) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { showMediaSheet = true },
                    enabled = windowOpen && !busy
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Send Business Media")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    enabled = windowOpen && !busy,
                    placeholder = {
                        Text(if (windowOpen) "Reply via Meta Cloud API" else "Use Template button above")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp)
                )
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            val txt = draft.trim()
                            draft = ""
                            onSendReply(txt)
                        }
                    },
                    enabled = windowOpen && !busy && draft.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Business Reply")
                }
            }
        }
    }

    if (showTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text("Send Approved Template Message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("Template Name (e.g. hello_world)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = templateLang,
                        onValueChange = { templateLang = it },
                        label = { Text("Language Code (e.g. en_US)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTemplateDialog = false
                        onSendTemplate(templateName, templateLang)
                    }
                ) { Text("Send Template") }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMediaSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMediaSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Send Cloud API Media", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("image", "document", "video").forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.uppercase()) },
                            onClick = { mediaType = type },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = mediaUrl,
                    onValueChange = { mediaUrl = it },
                    label = { Text("Public HTTPS Media URL (Selected: $mediaType)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mediaCaption,
                    onValueChange = { mediaCaption = it },
                    label = { Text("Caption (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        showMediaSheet = false
                        if (mediaUrl.startsWith("https://")) {
                            onSendMedia(mediaType, mediaUrl, mediaCaption.ifBlank { null })
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.width(4.dp))
                    Text("Send $mediaType via Meta Cloud API")
                }
            }
        }
    }
}
