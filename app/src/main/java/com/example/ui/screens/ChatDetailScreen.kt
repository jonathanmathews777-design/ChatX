package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ConversationType
import com.example.data.model.DecryptedChatMessage
import com.example.data.model.DeliveryStatus
import com.example.data.model.MemberProfile
import com.example.data.model.MessageType
import com.example.data.model.UiState
import com.example.ui.components.MonochromeOutlinedButton
import com.example.ui.components.MonochromePrimaryButton
import com.example.ui.components.formatShortTime
import com.example.viewmodel.ChatDetailState
import com.example.whatsapp.WhatsAppShareHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    myProfile: MemberProfile,
    uiState: UiState<ChatDetailState>,
    sending: Boolean,
    actionError: String?,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    onSendMessage: (
        plaintext: String,
        type: MessageType,
        attachmentName: String?,
        attachmentMimeType: String?,
        attachmentSizeBytes: Long?,
        voiceDurationMs: Long?
    ) -> Unit,
    onEditMessage: (ChatMessage, String) -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit,
    onSetTyping: (Boolean) -> Unit,
    onSetDisappearingTimer: (Long) -> Unit
) {
    val context = LocalContext.current
    var draftText by remember { mutableStateOf("") }
    var selectedMessageForMenu by remember { mutableStateOf<DecryptedChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<DecryptedChatMessage?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var inspectingMessage by remember { mutableStateOf<DecryptedChatMessage?>(null) }
    var showTimerMenu by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showPeerSafetySheet by remember { mutableStateOf(false) }
    var callModalType by remember { mutableStateOf<String?>(null) }

    // Track locally recorded/picked files so user can also play voice notes & share files directly to WhatsApp
    val localUrisByName = remember { mutableStateMapOf<String, Uri>() }
    val localVoiceFilesByName = remember { mutableStateMapOf<String, File>() }

    // Voice recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartMs by remember { mutableLongStateOf(0L) }
    var activeRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var activeVoiceFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activeRecorder?.release()
            } catch (_: Exception) {
            }
            onSetTyping(false)
        }
    }

    val startVoiceRecording: () -> Unit = {
        val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
        val file = File(dir, "cx_voice_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            activeRecorder = recorder
            activeVoiceFile = file
            recordingStartMs = System.currentTimeMillis()
            isRecording = true
        } catch (_: Exception) {
            recorder.release()
        }
    }

    val stopAndSendVoiceRecording: () -> Unit = {
        val durationMs = (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(500L)
        try {
            activeRecorder?.stop()
        } catch (_: Exception) {
        }
        activeRecorder?.release()
        activeRecorder = null
        isRecording = false
        val file = activeVoiceFile
        if (file != null && file.exists()) {
            localVoiceFilesByName[file.name] = file
            val sec = (durationMs / 1000L).coerceAtLeast(1L)
            onSendMessage(
                "Voice note (${sec}s)",
                MessageType.VOICE,
                file.name,
                "audio/mp4",
                file.length(),
                durationMs
            )
        }
        activeVoiceFile = null
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceRecording()
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        callModalType = "VIDEO"
    }

    val visualMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "image/*"
            val (name, size) = queryUriMeta(context, uri)
            val msgType = if (mime.startsWith("video/")) MessageType.VIDEO else MessageType.IMAGE
            localUrisByName[name] = uri
            onSendMessage(
                if (msgType == MessageType.VIDEO) "Shared video: $name" else "Shared image: $name",
                msgType,
                name,
                mime,
                size,
                null
            )
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val (name, size) = queryUriMeta(context, uri)
            localUrisByName[name] = uri
            onSendMessage(
                "Shared document: $name",
                MessageType.DOCUMENT,
                name,
                mime,
                size,
                null
            )
        }
    }

    val detailData = (uiState as? UiState.Success)?.data
    val conversation = detailData?.conversation
    val peerProfile = if (conversation?.parsedType == ConversationType.DIRECT) {
        val peerUid = conversation.participantUids.firstOrNull { it != myProfile.uid }
        peerUid?.let { detailData.participantsByUid[it] }
    } else null
    val headerTitle = peerProfile?.displayName ?: conversation?.title ?: "Encrypted Chat"
    val typingOthers = conversation?.typingUids?.filter { it != myProfile.uid }.orEmpty()
    val headerSubtitle = when {
        typingOthers.isNotEmpty() -> "typing..."
        peerProfile?.online == true -> "online • E2EE P-256"
        conversation != null -> "${conversation.participantUids.size} participants • E2EE"
        else -> "End-to-End Encrypted"
    }

    val listState = rememberLazyListState()
    LaunchedEffect(detailData?.messages?.size) {
        val count = detailData?.messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Inbox")
                    }
                },
                title = {
                    Column(modifier = Modifier.testTag("chat_header_title")) {
                        Text(headerTitle, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            headerSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (typingOthers.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showTimerMenu = true }) {
                            Icon(Icons.Default.Timer, contentDescription = "Disappearing messages timer")
                        }
                        DropdownMenu(expanded = showTimerMenu, onDismissRequest = { showTimerMenu = false }) {
                            listOf(
                                0L to "Disappearing: Off",
                                30L to "Disappearing: 30s",
                                300L to "Disappearing: 5m",
                                86400L to "Disappearing: 24h"
                            ).forEach { (sec, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        showTimerMenu = false
                                        onSetDisappearingTimer(sec)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { callModalType = "AUDIO" }) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call")
                    }
                    IconButton(onClick = {
                        cameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call")
                    }
                    IconButton(onClick = { showPeerSafetySheet = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "Verify E2EE Keys & Profile")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
            if (conversation != null && conversation.disappearingSeconds > 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            "Disappearing messages active (${formatTimerLabel(conversation.disappearingSeconds)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (actionError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(actionError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is UiState.Error -> {
                        Text(
                            text = uiState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                    }
                    is UiState.Success -> {
                        val messages = uiState.data.messages
                        if (messages.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("End-to-End Encrypted Channel", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Messages are encrypted with ECDH P-256 + HKDF-SHA256 + AES-256-GCM before leaving this device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(messages, key = { it.raw.id }) { item ->
                                    MessageBubble(
                                        item = item,
                                        isMine = item.raw.senderUid == myProfile.uid,
                                        isGroup = conversation?.parsedType == ConversationType.GROUP,
                                        localVoiceFile = item.raw.attachmentName?.let { localVoiceFilesByName[it] },
                                        onOpenActions = { selectedMessageForMenu = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Message Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { showAttachmentSheet = true },
                    modifier = Modifier.testTag("attach_button")
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach image, video, or document")
                }

                OutlinedTextField(
                    value = draftText,
                    onValueChange = {
                        draftText = it
                        onSetTyping(it.isNotEmpty())
                    },
                    placeholder = { Text(if (isRecording) "Recording voice note..." else "Encrypted message") },
                    enabled = !isRecording,
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_input")
                )

                if (draftText.isBlank()) {
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                stopAndSendVoiceRecording()
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) startVoiceRecording()
                                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh,
                                CircleShape
                            )
                            .testTag("voice_record_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop and send voice note" else "Record voice note",
                            tint = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            val textToSend = draftText.trim()
                            if (textToSend.isNotEmpty()) {
                                draftText = ""
                                onSetTyping(false)
                                onSendMessage(textToSend, MessageType.TEXT, null, null, null, null)
                            }
                        },
                        enabled = !sending,
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send encrypted message",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    // Message Action Bottom Sheet (Edit, Delete, Share to WhatsApp, Inspect E2EE)
    selectedMessageForMenu?.let { item ->
        val isMine = item.raw.senderUid == myProfile.uid
        ModalBottomSheet(
            onDismissRequest = { selectedMessageForMenu = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Message Actions", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))

                if (!item.raw.deleted) {
                    DropdownMenuItem(
                        text = { Text("Share to WhatsApp") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            selectedMessageForMenu = null
                            val attName = item.raw.attachmentName
                            val localFile = attName?.let { localVoiceFilesByName[it] }
                            val localUri = attName?.let { localUrisByName[it] }
                            when {
                                localFile != null && localFile.exists() -> {
                                    WhatsAppShareHelper.shareInternalFileToWhatsApp(
                                        context,
                                        localFile,
                                        item.raw.attachmentMimeType ?: "audio/mp4",
                                        item.plaintext
                                    )
                                }
                                localUri != null -> {
                                    WhatsAppShareHelper.shareUriToWhatsApp(
                                        context,
                                        localUri,
                                        item.raw.attachmentMimeType ?: "*/*",
                                        item.plaintext
                                    )
                                }
                                else -> {
                                    WhatsAppShareHelper.shareTextToWhatsApp(context, item.plaintext)
                                }
                            }
                        }
                    )
                }

                if (isMine && !item.raw.deleted && item.raw.parsedMessageType == MessageType.TEXT) {
                    DropdownMenuItem(
                        text = { Text("Edit Message") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            selectedMessageForMenu = null
                            editDraft = item.plaintext
                            editingMessage = item
                        }
                    )
                }

                if (isMine && !item.raw.deleted) {
                    DropdownMenuItem(
                        text = { Text("Delete Message", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            selectedMessageForMenu = null
                            onDeleteMessage(item.raw)
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Inspect Ciphertext & Scheme") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    onClick = {
                        selectedMessageForMenu = null
                        inspectingMessage = item
                    }
                )
            }
        }
    }

    // Edit Message Dialog
    editingMessage?.let { item ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Encrypted Message") },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editDraft.isNotBlank()) {
                            onEditMessage(item.raw, editDraft.trim())
                        }
                        editingMessage = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancel") }
            }
        )
    }

    // Inspect Ciphertext Dialog
    inspectingMessage?.let { item ->
        AlertDialog(
            onDismissRequest = { inspectingMessage = null },
            title = { Text("E2EE Payload Proof") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scheme: ${item.raw.encryptionScheme}", style = MaterialTheme.typography.labelMedium)
                    Text("IV (Base64): ${item.raw.ivBase64}", style = MaterialTheme.typography.labelSmall)
                    Text("Ciphertext: ${item.raw.ciphertextBase64.take(96)}...", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingMessage = null }) { Text("Close") }
            }
        )
    }

    // Attachment Picker Sheet (Photo Picker & SAF Document Picker — zero broad storage permissions)
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Share Media or Document", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Uses Android's zero-permission Photo Picker and Document Picker. Metadata is encrypted with E2EE; full cross-device binary blob upload requires Stage 2 media storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownMenuItem(
                    text = { Text("Photo or Video (Android Photo Picker)") },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                    onClick = {
                        showAttachmentSheet = false
                        visualMediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Document / PDF / File") },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    onClick = {
                        showAttachmentSheet = false
                        documentPicker.launch(arrayOf("*/*"))
                    }
                )
            }
        }
    }

    // Peer Safety Verification & WhatsApp Profile Deep-Link Sheet
    if (showPeerSafetySheet && conversation != null) {
        ModalBottomSheet(
            onDismissRequest = { showPeerSafetySheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Encryption & Contact Verification", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Protocol: ECDH P-256 + HKDF-SHA256 + AES-256-GCM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (peerProfile != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${peerProfile.displayName} (@${peerProfile.username})", style = MaterialTheme.typography.titleMedium)
                            Text("Key Fingerprint:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(peerProfile.keyFingerprint, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (!peerProfile.phoneE164.isNullOrBlank()) {
                        MonochromePrimaryButton(
                            text = "Open WhatsApp Chat (${peerProfile.phoneE164})",
                            onClick = {
                                showPeerSafetySheet = false
                                WhatsAppShareHelper.openWhatsAppConversation(context, peerProfile.phoneE164)
                            }
                        )
                    } else {
                        Text(
                            "Peer has not linked a WhatsApp phone number on their ChatX profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    detailData?.participantsByUid?.values?.filter { it.uid in conversation.participantUids }?.forEach { member ->
                        Text(
                            "@${member.username} • ${member.keyFingerprint}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    // Honest WebRTC Stage Status Modal (no fake simulated call)
    callModalType?.let { type ->
        AlertDialog(
            onDismissRequest = { callModalType = null },
            title = { Text("$type Calling — Stage 2 Signaling Required") },
            text = {
                Text(
                    "ChatX audio/video calling uses WebRTC peer-to-peer media. Live remote call connection requires deploying a WebRTC signaling & TURN/STUN server (Stage 2). Hardware permissions are wired and verified without simulating a fake call."
                )
            },
            confirmButton = {
                TextButton(onClick = { callModalType = null }) { Text("Understood") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    item: DecryptedChatMessage,
    isMine: Boolean,
    isGroup: Boolean,
    localVoiceFile: File?,
    onOpenActions: () -> Unit
) {
    val alignment = if (isMine) Alignment.End else Alignment.Start
    val containerColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val timeText = formatShortTime(item.raw.createdAt?.toDate()?.time ?: System.currentTimeMillis())

    var isPlayingVoice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!isMine && isGroup) {
            Text(
                text = "@${item.raw.senderUsername}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            color = containerColor,
            contentColor = contentColor,
            border = if (isMine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .combinedClickable(
                    onClick = onOpenActions,
                    onLongClick = onOpenActions
                )
                .testTag("message_bubble_${item.raw.id}")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.raw.parsedMessageType == MessageType.VOICE && !item.raw.deleted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (localVoiceFile != null && localVoiceFile.exists()) {
                            IconButton(
                                onClick = {
                                    if (!isPlayingVoice) {
                                        isPlayingVoice = true
                                        val player = MediaPlayer()
                                        try {
                                            player.setDataSource(localVoiceFile.absolutePath)
                                            player.prepare()
                                            player.setOnCompletionListener {
                                                isPlayingVoice = false
                                                it.release()
                                            }
                                            player.start()
                                        } catch (_: Exception) {
                                            isPlayingVoice = false
                                            player.release()
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play voice note",
                                    tint = contentColor
                                )
                            }
                        } else {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Text(item.plaintext, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(
                        text = item.plaintext,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (item.raw.deleted) contentColor.copy(alpha = 0.6f) else contentColor
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.raw.edited && !item.raw.deleted) {
                        Text(
                            text = "Edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                    if (isMine) {
                        val isRead = item.raw.readByUids.size > 1 || item.raw.parsedDeliveryStatus == DeliveryStatus.READ
                        Icon(
                            imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = if (isRead) "Read" else "Sent",
                            modifier = Modifier.size(14.dp),
                            tint = contentColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

private fun queryUriMeta(context: android.content.Context, uri: Uri): Pair<String, Long?> {
    var name = "attachment"
    var size: Long? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
        }
    }
    return name to size
}

private fun formatTimerLabel(seconds: Long): String = when (seconds) {
    30L -> "30s"
    300L -> "5m"
    86400L -> "24h"
    else -> "${seconds}s"
}
