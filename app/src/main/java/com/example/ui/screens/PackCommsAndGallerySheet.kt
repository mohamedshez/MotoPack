package com.example.ui.screens

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.audio.PttPlaybackUiState
import com.example.audio.PttRecordingUiState
import com.example.data.model.DecryptedPackMessage
import com.example.data.model.PackGalleryPhoto
import com.example.data.model.RidePack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackCommsAndGalleryBottomSheet(
    activePack: RidePack?,
    currentUserId: String,
    messages: List<DecryptedPackMessage>,
    galleryPhotos: List<PackGalleryPhoto>,
    initialTabIndex: Int = 0,
    pttRecordingState: PttRecordingUiState = PttRecordingUiState(),
    pttPlaybackState: PttPlaybackUiState = PttPlaybackUiState(),
    pttAutoPlayEnabled: Boolean = true,
    pendingVoiceCount: Int = 0,
    onSendMessage: (String) -> Unit,
    onStartPttRecording: () -> Unit = {},
    onStopAndSendPttRecording: () -> Unit = {},
    onCancelPttRecording: () -> Unit = {},
    onTogglePlayVoice: (DecryptedPackMessage) -> Unit = {},
    onTogglePttAutoPlay: (Boolean) -> Unit = {},
    onUploadPhoto: (Uri, String, String) -> Unit,
    onDeletePhoto: (PackGalleryPhoto) -> Unit,
    onNavigateToPackSetup: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var previewPhoto by remember { mutableStateOf<PackGalleryPhoto?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingPhotoUri = uri
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .testTag("map_pack_comms_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "E2E Encrypted",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = activePack?.name ?: "Pack Comms & Gallery",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Real-time convoy chat & ride gallery",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close pack comms")
                }
            }

            if (activePack == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Join or Create a Pack First", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Group chat and shared ride photos sync live across all members in your encrypted riding pack.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateToPackSetup()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Open Pack Setup")
                        }
                    }
                }
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("comms_tab_chat")
                    ) {
                        Text("Group Chat (${messages.count { it.recipientId.isBlank() }})")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("comms_tab_gallery")
                    ) {
                        Text("Gallery (${galleryPhotos.size})")
                    }
                }

                if (selectedTab == 0) {
                    LiveGroupChatPane(
                        currentUserId = currentUserId,
                        messages = messages.filter { it.recipientId.isBlank() },
                        pttRecordingState = pttRecordingState,
                        pttPlaybackState = pttPlaybackState,
                        pttAutoPlayEnabled = pttAutoPlayEnabled,
                        pendingVoiceCount = pendingVoiceCount,
                        onSendMessage = onSendMessage,
                        onStartPttRecording = onStartPttRecording,
                        onStopAndSendPttRecording = onStopAndSendPttRecording,
                        onCancelPttRecording = onCancelPttRecording,
                        onTogglePlayVoice = onTogglePlayVoice,
                        onTogglePttAutoPlay = onTogglePttAutoPlay,
                        onPickPhoto = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    PackGalleryPane(
                        photos = galleryPhotos,
                        onPickPhoto = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onSelectPhoto = { previewPhoto = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Caption Dialog after choosing a photo with zero-permission Photo Picker
    pendingPhotoUri?.let { uri ->
        var caption by remember { mutableStateOf("") }
        var locationLabel by remember { mutableStateOf(activePack?.activeRouteName ?: "Convoy Stop") }
        AlertDialog(
            onDismissRequest = { pendingPhotoUri = null },
            title = { Text("Share Photo to Pack Gallery") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected ride photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Caption (e.g. Fuel & coffee stop)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gallery_caption_input")
                    )
                    OutlinedTextField(
                        value = locationLabel,
                        onValueChange = { locationLabel = it },
                        label = { Text("Location Tag") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUploadPhoto(uri, caption, locationLabel)
                        pendingPhotoUri = null
                        selectedTab = 1
                    },
                    modifier = Modifier.testTag("confirm_upload_gallery_photo")
                ) {
                    Text("Share Photo")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPhotoUri = null }) { Text("Cancel") }
            }
        )
    }

    // Full-screen Photo Preview Dialog
    previewPhoto?.let { photo ->
        val decodedBytes = remember(photo.imageDataBase64) {
            runCatching { Base64.decode(photo.imageDataBase64, Base64.DEFAULT) }.getOrNull()
        }
        AlertDialog(
            onDismissRequest = { previewPhoto = null },
            title = {
                Column {
                    Text(photo.uploaderName, style = MaterialTheme.typography.titleMedium)
                    if (photo.locationLabel.isNotBlank()) {
                        Text(
                            photo.locationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (decodedBytes != null) {
                        AsyncImage(
                            model = decodedBytes,
                            contentDescription = photo.caption.ifBlank { "Pack ride photo" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                        )
                    }
                    if (photo.caption.isNotBlank()) {
                        Text(photo.caption, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewPhoto = null }) { Text("Close") }
            },
            dismissButton = if (photo.uploaderId == currentUserId) {
                {
                    TextButton(
                        onClick = {
                            onDeletePhoto(photo)
                            previewPhoto = null
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            } else null
        )
    }
}

@Composable
private fun LiveGroupChatPane(
    currentUserId: String,
    messages: List<DecryptedPackMessage>,
    pttRecordingState: PttRecordingUiState,
    pttPlaybackState: PttPlaybackUiState,
    pttAutoPlayEnabled: Boolean,
    pendingVoiceCount: Int,
    onSendMessage: (String) -> Unit,
    onStartPttRecording: () -> Unit,
    onStopAndSendPttRecording: () -> Unit,
    onCancelPttRecording: () -> Unit,
    onTogglePlayVoice: (DecryptedPackMessage) -> Unit,
    onTogglePttAutoPlay: (Boolean) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val quickReplies = remember {
        listOf("Fuel needed soon", "Pulling over", "Hazard ahead!", "Wait up at junction", "All clear")
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PttAutoPlayChip(
                enabled = pttAutoPlayEnabled,
                pendingVoiceCount = pendingVoiceCount,
                onToggle = onTogglePttAutoPlay
            )
            Text(
                text = "Low-BW Voice • AMR 8kHz",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(quickReplies) { preset ->
                SuggestionChip(
                    onClick = { onSendMessage(preset) },
                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No group messages yet. Tap a quick callout above or message your pack.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMe = msg.senderId == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isMe) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = if (isMe) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            } else null,
                            modifier = Modifier.fillMaxWidth(0.84f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isMe) "You" else msg.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatRelativeTime(msg.timestampMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (msg.messageType == "voice" && msg.audioBase64.isNotBlank()) {
                                    VoiceMessageBubbleContent(
                                        message = msg,
                                        playbackState = pttPlaybackState,
                                        isMe = isMe,
                                        onTogglePlay = onTogglePlayVoice
                                    )
                                } else {
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        PttLiveRecordingBanner(
            recordingState = pttRecordingState,
            onStopAndSend = onStopAndSendPttRecording,
            onCancel = onCancelPttRecording
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onPickPhoto,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("chat_attach_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = "Share ride photo",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message pack...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("map_group_chat_input")
            )
            if (draft.isBlank()) {
                PttHoldOrTapButton(
                    isRecording = pttRecordingState.isRecording,
                    compactLabel = true,
                    testTag = "map_chat_ptt_button",
                    onStartRecording = onStartPttRecording,
                    onStopAndSendRecording = onStopAndSendPttRecording
                )
            } else {
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onSendMessage(draft)
                            draft = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("map_group_chat_send")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send group message",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun PackGalleryPane(
    photos: List<PackGalleryPhoto>,
    onPickPhoto: () -> Unit,
    onSelectPhoto: (PackGalleryPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onPickPhoto,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("add_gallery_photo_button")
        ) {
            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Photo to Pack Gallery")
        }

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No ride photos yet",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Share scenic viewpoints, cafe stops, or road hazard photos with your pack.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("pack_gallery_grid"),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    PackGalleryTile(photo = photo, onClick = { onSelectPhoto(photo) })
                }
            }
        }
    }
}

@Composable
fun PackGalleryTile(
    photo: PackGalleryPhoto,
    onClick: () -> Unit
) {
    val imageBytes = remember(photo.imageDataBase64) {
        runCatching { Base64.decode(photo.imageDataBase64, Base64.DEFAULT) }.getOrNull()
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("gallery_photo_${photo.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (imageBytes != null) {
                    AsyncImage(
                        model = imageBytes,
                        contentDescription = photo.caption.ifBlank { "Ride photo" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = photo.uploaderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = photo.caption.ifBlank { photo.locationLabel.ifBlank { "Ride moment" } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
