package com.example.ui.screens

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.audio.PttPlaybackUiState
import com.example.audio.PttRecordingUiState
import com.example.data.local.RiderSettings
import com.example.data.model.DecryptedPackMessage
import com.example.data.model.DecryptedRiderState
import com.example.data.model.PackGalleryPhoto
import com.example.data.model.RidePack
import com.example.data.model.UserProfile
import com.example.mesh.MeshVpnStatus
import com.example.mesh.MotoPackMeshVpnService
import com.example.mesh.P2pMeshUiState
import com.example.ui.theme.HudThemeMode
import com.example.ui.theme.TelemetryMonoFontFamily

@Composable
fun PackHubScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    userPacks: List<RidePack>,
    activePack: RidePack?,
    riders: List<DecryptedRiderState>,
    messages: List<DecryptedPackMessage>,
    packPhotos: List<PackGalleryPhoto> = emptyList(),
    directMessageTarget: DecryptedRiderState?,
    settings: RiderSettings,
    isEffectiveOffline: Boolean,
    pendingBreadcrumbsCount: Int,
    pttRecordingState: PttRecordingUiState = PttRecordingUiState(),
    pttPlaybackState: PttPlaybackUiState = PttPlaybackUiState(),
    pendingVoiceCount: Int = 0,
    p2pMeshState: P2pMeshUiState = P2pMeshUiState(),
    meshVpnStatus: MeshVpnStatus = MeshVpnStatus(),
    onCreatePack: (name: String, bike: String, phone: String) -> Unit,
    onJoinPack: (inviteCode: String) -> Unit,
    onSwitchPack: (packId: String) -> Unit,
    onSelectMessageTarget: (DecryptedRiderState?) -> Unit,
    onSendMessage: (text: String, recipientId: String) -> Unit,
    onStartPttRecording: (recipientId: String) -> Unit = {},
    onStopAndSendPttRecording: (recipientId: String) -> Unit = {},
    onCancelPttRecording: () -> Unit = {},
    onTogglePlayVoice: (DecryptedPackMessage) -> Unit = {},
    onSetPttAutoPlay: (Boolean) -> Unit = {},
    onRingRider: (DecryptedRiderState) -> Unit,
    onUpdateProfile: (name: String, bike: String, phone: String) -> Unit,
    onSetHudMode: (HudThemeMode) -> Unit,
    onSetEcoMode: (Boolean) -> Unit,
    onSetManualOffline: (Boolean) -> Unit,
    onSetP2pMesh: (Boolean) -> Unit = {},
    onSetMeshVpn: (Boolean) -> Unit = {},
    onConnectWifiDirectPeer: (String) -> Unit = {},
    onAddManualMeshPeer: (String) -> Unit = {},
    onSetSeparationKm: (Double) -> Unit,
    onUploadGalleryPhoto: (Uri, String, String) -> Unit = { _, _, _ -> },
    onDeleteGalleryPhoto: (PackGalleryPhoto) -> Unit = {},
    onOpenChallengesAndReports: () -> Unit = {},
    onSignOut: () -> Unit
) {
    var selectedPackTab by rememberSaveable { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAddMeshPeerDialog by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var previewPhoto by remember { mutableStateOf<PackGalleryPhoto?>(null) }
    var chatInput by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingPhotoUri = uri
        }
    }

    val filteredMessages = remember(messages, directMessageTarget, currentUserId) {
        if (directMessageTarget == null) {
            messages.filter { it.recipientId.isBlank() }
        } else {
            messages.filter {
                (it.senderId == currentUserId && it.recipientId == directMessageTarget.userId) ||
                    (it.senderId == directMessageTarget.userId && it.recipientId == currentUserId)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pack Hub", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = if (activePack != null) {
                            "${activePack.name} • Code ${activePack.inviteCode} • AES-256 E2E"
                        } else {
                            "Create or join a riding pack"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showProfileDialog = true }) {
                        Icon(Icons.Default.Person, contentDescription = "Edit Rider Profile")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out")
                    }
                }
            }

            // Pack Create / Join Actions + Pack Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_pack_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Pack")
                }
                OutlinedButton(
                    onClick = { showJoinDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("join_pack_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Join Code")
                }
            }

            if (userPacks.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(userPacks, key = { it.id }) { pack ->
                        FilterChip(
                            selected = pack.id == activePack?.id,
                            onClick = { onSwitchPack(pack.id) },
                            label = { Text("${pack.name} (${pack.inviteCode})") }
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedPackTab == 0,
                    onClick = { selectedPackTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 3),
                    modifier = Modifier.testTag("pack_tab_comms")
                ) {
                    Text("Comms", maxLines = 1)
                }
                SegmentedButton(
                    selected = selectedPackTab == 1,
                    onClick = { selectedPackTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 3),
                    modifier = Modifier.testTag("pack_tab_crew")
                ) {
                    Text("Crew (${riders.size})", maxLines = 1)
                }
                SegmentedButton(
                    selected = selectedPackTab == 2,
                    onClick = { selectedPackTab = 2 },
                    shape = SegmentedButtonDefaults.itemShape(2, 3),
                    modifier = Modifier.testTag("pack_tab_settings")
                ) {
                    Text("Mesh & HUD", maxLines = 1)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectedPackTab == 0) {
                if (activePack == null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Create or join a riding pack above to unlock encrypted pack chat and Push-to-Talk voice callouts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    // Encrypted Group / Direct Rider Chat
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Encrypted Pack Comms", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = if (directMessageTarget == null) "All Riders" else "DM: ${directMessageTarget.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = directMessageTarget == null,
                                    onClick = { onSelectMessageTarget(null) },
                                    label = { Text("Pack Broadcast") }
                                )
                            }
                            items(riders.filter { it.userId != currentUserId }, key = { it.userId }) { rider ->
                                FilterChip(
                                    selected = directMessageTarget?.userId == rider.userId,
                                    onClick = { onSelectMessageTarget(rider) },
                                    label = { Text(rider.displayName.substringBefore(" ")) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PttAutoPlayChip(
                                enabled = settings.pttAutoPlay,
                                pendingVoiceCount = pendingVoiceCount,
                                onToggle = onSetPttAutoPlay
                            )
                            Text(
                                text = "AMR 8kHz Low-BW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (filteredMessages.isEmpty()) {
                            Text(
                                text = "No messages yet. Send an encrypted status check or Push-to-Talk voice callout.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                filteredMessages.takeLast(8).forEach { msg ->
                                    val isMe = msg.senderId == currentUserId
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isMe) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = if (isMe) "You" else msg.senderName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
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

                        PttLiveRecordingBanner(
                            recordingState = pttRecordingState,
                            onStopAndSend = { onStopAndSendPttRecording(directMessageTarget?.userId ?: "") },
                            onCancel = onCancelPttRecording
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                placeholder = {
                                    Text(
                                        if (directMessageTarget == null) "Message pack..."
                                        else "DM ${directMessageTarget.displayName}..."
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pack_chat_input")
                            )
                            if (chatInput.isBlank()) {
                                val targetId = directMessageTarget?.userId ?: ""
                                PttHoldOrTapButton(
                                    isRecording = pttRecordingState.isRecording,
                                    compactLabel = true,
                                    testTag = "pack_hub_ptt_button",
                                    onStartRecording = { onStartPttRecording(targetId) },
                                    onStopAndSendRecording = { onStopAndSendPttRecording(targetId) }
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        if (chatInput.isNotBlank()) {
                                            onSendMessage(chatInput, directMessageTarget?.userId ?: "")
                                            chatInput = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .testTag("send_chat_button")
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
            }
                }
            } else if (selectedPackTab == 1) {
                item {
                    OutlinedButton(
                        onClick = onOpenChallengesAndReports,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pack_open_awards_reports_button")
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pack Challenges, Milestones & PDF Trip Reports")
                    }
                }

                if (activePack == null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Join or create a pack to view riders in your convoy and share ride gallery photos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    // Shared Ride Photo Gallery Card
                    item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pack_hub_gallery_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pack Ride Gallery (${packPhotos.size})", style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("pack_hub_add_photo_button")
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Photo")
                            }
                        }

                        if (packPhotos.isEmpty()) {
                            Text(
                                text = "Share gas stop snaps, scenic passes, or road condition photos with your pack.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(packPhotos, key = { it.id }) { photo ->
                                    Box(modifier = Modifier.width(156.dp)) {
                                        PackGalleryTile(
                                            photo = photo,
                                            onClick = { previewPhoto = photo }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pack Riders Roster
            item {
                Text("Pack Riders (${riders.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(riders, key = { it.userId }) { rider ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (rider.userId == currentUserId) "${rider.displayName} (You)" else rider.displayName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${rider.bikeModel} • ${rider.batteryPercent}% batt • ${String.format("%.1f km", rider.distanceFromMeKm)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (rider.userId != currentUserId) {
                            IconButton(onClick = { onRingRider(rider) }) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Ring ${rider.displayName}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
                }
            } else {
                // HUD, Battery & Offline Controls
                item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Cockpit HUD & Battery Efficiency", style = MaterialTheme.typography.titleMedium)

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            HudThemeMode.AUTO to "Auto",
                            HudThemeMode.DAYLIGHT to "Daylight",
                            HudThemeMode.NIGHT_HUD to "Night HUD"
                        )
                        modes.forEachIndexed { idx, (mode, label) ->
                            SegmentedButton(
                                selected = settings.hudThemeMode == mode,
                                onClick = { onSetHudMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(idx, modes.size)
                            ) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery Efficiency Mode", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "20s GPS interval, OLED Night HUD & 10 km weather polling",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.ecoRideMode,
                            onCheckedChange = onSetEcoMode,
                            modifier = Modifier.testTag("battery_efficiency_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Offline Dead-Zone Mode", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Queued breadcrumbs: $pendingBreadcrumbsCount • ${if (isEffectiveOffline) "Offline active" else "Cloud synced"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.manualOfflineMode,
                            onCheckedChange = onSetManualOffline,
                            modifier = Modifier.testTag("offline_deadzone_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // P2P & Mesh VPN Controls alongside Offline Mode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "P2P Convoy Mesh & Encrypted VPN",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("P2P Convoy Mesh (Wi-Fi Direct / LAN)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (p2pMeshState.isActive) {
                                    "${p2pMeshState.activeSocketPeers.size} peers • :${p2pMeshState.localTcpPort} • TX ${p2pMeshState.packetsSent} / Relay ${p2pMeshState.packetsRelayed} / RX ${p2pMeshState.packetsReceived}"
                                } else {
                                    "Multi-hop AES-256-GCM relay for GPS, chat & PTT in zero-signal canyons"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p2pMeshState.isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.p2pMeshEnabled,
                            onCheckedChange = onSetP2pMesh,
                            modifier = Modifier.testTag("p2p_mesh_switch")
                        )
                    }

                    if (settings.p2pMeshEnabled || p2pMeshState.isActive) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("p2p_mesh_status_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = p2pMeshState.transportSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { showAddMeshPeerDialog = true },
                                        modifier = Modifier.testTag("add_mesh_peer_ip_button")
                                    ) {
                                        Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Peer IP")
                                    }
                                }

                                if (p2pMeshState.wifiDirectPeers.isNotEmpty()) {
                                    Text(
                                        text = "Nearby Wi-Fi Direct Riders (${p2pMeshState.wifiDirectPeers.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    p2pMeshState.wifiDirectPeers.forEach { peer ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(peer.deviceName, style = MaterialTheme.typography.bodySmall)
                                                Text(
                                                    "${peer.statusLabel} • ${peer.deviceAddress}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            OutlinedButton(onClick = { onConnectWifiDirectPeer(peer.deviceAddress) }) {
                                                Text("Link")
                                            }
                                        }
                                    }
                                }

                                if (p2pMeshState.activeSocketPeers.isNotEmpty()) {
                                    Text(
                                        text = "Mesh Endpoints: " + p2pMeshState.activeSocketPeers.joinToString { "${it.hostAddress}:${it.port}" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = TelemetryMonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    val previewVirtualIp = meshVpnStatus.virtualIp.ifBlank {
                        MotoPackMeshVpnService.computeVirtualMeshIp(
                            packId = activePack?.id ?: "solo_pack",
                            userId = currentUserId
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Encrypted Mesh VPN (10.66.x.y)", style = MaterialTheme.typography.titleSmall)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (meshVpnStatus.isActive) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = if (meshVpnStatus.isActive) "TUN UP" else "Standby",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (meshVpnStatus.isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Virtual IP: $previewVirtualIp • ${meshVpnStatus.cipherSuite} • Routed ${meshVpnStatus.packetsRouted} pkts",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = TelemetryMonoFontFamily,
                                color = if (meshVpnStatus.isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.meshVpnEnabled,
                            onCheckedChange = onSetMeshVpn,
                            modifier = Modifier.testTag("mesh_vpn_switch")
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Convoy Separation Alert", style = MaterialTheme.typography.titleSmall)
                            Text(
                                String.format("%.1f km", settings.separationAlertKm),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = settings.separationAlertKm.toFloat(),
                            onValueChange = { onSetSeparationKm(it.toDouble()) },
                            valueRange = 0.5f..10f
                        )
                    }
                }
            }
                }
            }
        }
    }

    if (showCreateDialog) {
        var packName by remember { mutableStateOf("Peak District Pack") }
        var bikeModel by remember { mutableStateOf(userProfile?.bikeModel ?: "Yamaha Tracer 9 GT") }
        var phone by remember { mutableStateOf(userProfile?.phoneNumber ?: "") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Encrypted Pack") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = packName, onValueChange = { packName = it }, label = { Text("Pack Name") })
                    OutlinedTextField(value = bikeModel, onValueChange = { bikeModel = it }, label = { Text("Your Motorcycle") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone (optional, for Dial)") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePack(packName, bikeModel, phone)
                        showCreateDialog = false
                    },
                    modifier = Modifier.testTag("confirm_create_pack_button")
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showJoinDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join Riding Pack") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("6-Character Invite Code") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (code.isNotBlank()) {
                            onJoinPack(code)
                            showJoinDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_join_pack_button")
                ) { Text("Join") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showProfileDialog) {
        var name by remember { mutableStateOf(userProfile?.displayName ?: "") }
        var bike by remember { mutableStateOf(userProfile?.bikeModel ?: "") }
        var phone by remember { mutableStateOf(userProfile?.phoneNumber ?: "") }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Rider Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") })
                    OutlinedTextField(value = bike, onValueChange = { bike = it }, label = { Text("Motorcycle Model") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Contact Phone (optional)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateProfile(name, bike, phone)
                    showProfileDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingPhotoUri?.let { uri ->
        var caption by remember { mutableStateOf("") }
        var locationLabel by remember { mutableStateOf(activePack?.activeRouteName ?: "Pack Ride") }
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
                        label = { Text("Caption") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                Button(onClick = {
                    onUploadGalleryPhoto(uri, caption, locationLabel)
                    pendingPhotoUri = null
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPhotoUri = null }) { Text("Cancel") }
            }
        )
    }

    previewPhoto?.let { photo ->
        val decodedBytes = remember(photo.imageDataBase64) {
            runCatching { Base64.decode(photo.imageDataBase64, Base64.DEFAULT) }.getOrNull()
        }
        AlertDialog(
            onDismissRequest = { previewPhoto = null },
            title = { Text(photo.uploaderName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (decodedBytes != null) {
                        AsyncImage(
                            model = decodedBytes,
                            contentDescription = photo.caption.ifBlank { "Ride photo" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
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
                    TextButton(onClick = {
                        onDeleteGalleryPhoto(photo)
                        previewPhoto = null
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            } else null
        )
    }

    if (showAddMeshPeerDialog) {
        var peerIp by remember { mutableStateOf("192.168.49.1") }
        AlertDialog(
            onDismissRequest = { showAddMeshPeerDialog = false },
            title = { Text("Add P2P Mesh Peer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a pack rider's Wi-Fi Direct / hotspot IP (default TCP port 47821):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = peerIp,
                        onValueChange = { peerIp = it },
                        label = { Text("Peer IP Address") },
                        singleLine = true,
                        modifier = Modifier.testTag("manual_mesh_peer_ip_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (peerIp.isNotBlank()) {
                            onAddManualMeshPeer(peerIp.trim())
                            showAddMeshPeerDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_mesh_peer_button")
                ) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { showAddMeshPeerDialog = false }) { Text("Cancel") }
            }
        )
    }
}
