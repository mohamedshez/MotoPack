package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audio.PttPlaybackUiState
import com.example.audio.PttRecordingUiState
import com.example.audio.PttVoiceEngine
import com.example.data.model.DecryptedPackMessage
import com.example.ui.theme.TelemetryMonoFontFamily

@Composable
fun rememberPttRecordAction(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopAndSendRecording: () -> Unit,
    onPermissionDeniedMessage: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartRecording()
        } else {
            onPermissionDeniedMessage()
        }
    }

    return remember(context, isRecording, onStartRecording, onStopAndSendRecording) {
        {
            if (isRecording) {
                onStopAndSendRecording()
            } else {
                val hasMic = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    onStartRecording()
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
}

@Composable
fun PttHoldOrTapButton(
    isRecording: Boolean,
    enabled: Boolean = true,
    compactLabel: Boolean = false,
    testTag: String = "ptt_record_button",
    onStartRecording: () -> Unit,
    onStopAndSendRecording: () -> Unit,
    onPermissionDenied: () -> Unit = {}
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var pressDownAtMs by remember { mutableLongStateOf(0L) }
    var startedOnThisPress by remember { mutableStateOf(false) }

    val triggerAudioAction = rememberPttRecordAction(
        isRecording = isRecording,
        onStartRecording = onStartRecording,
        onStopAndSendRecording = onStopAndSendRecording,
        onPermissionDeniedMessage = onPermissionDenied
    )

    Surface(
        onClick = {
            if (!enabled) return@Surface
            if (startedOnThisPress) {
                // This tap already started (and either latched or sent via hold-release) in pointerInput
                startedOnThisPress = false
                return@Surface
            }
            triggerAudioAction()
        },
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = when {
            !enabled -> scheme.surfaceVariant.copy(alpha = 0.7f)
            isRecording -> scheme.error
            else -> scheme.secondary
        },
        border = BorderStroke(
            width = if (isRecording) 2.dp else 1.5.dp,
            color = if (isRecording) scheme.onError.copy(alpha = 0.85f) else scheme.outline.copy(alpha = 0.4f)
        ),
        shadowElevation = if (isRecording) 8.dp else 4.dp,
        modifier = Modifier
            .height(48.dp)
            .pointerInput(enabled, isRecording) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasMic || isRecording) {
                        startedOnThisPress = false
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    pressDownAtMs = System.currentTimeMillis()
                    startedOnThisPress = true
                    onStartRecording()
                    val up = waitForUpOrCancellation()
                    val heldMs = System.currentTimeMillis() - pressDownAtMs
                    if (up != null && heldMs >= 480L) {
                        // Classic Push-to-Talk: held & released -> transmit immediately
                        onStopAndSendRecording()
                    }
                }
            }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compactLabel) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop and send voice message" else "Push to talk voice message",
                tint = if (isRecording) scheme.onError else scheme.onSecondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = when {
                    isRecording -> "TX • SEND"
                    compactLabel -> "PTT"
                    else -> "Hold / Tap PTT"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isRecording) scheme.onError else scheme.onSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PttLiveRecordingBanner(
    recordingState: PttRecordingUiState,
    onStopAndSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!recordingState.isRecording) return
    val scheme = MaterialTheme.colorScheme
    val elapsedSec = recordingState.elapsedMs / 1000
    val maxSec = recordingState.maxDurationMs / 1000
    val estKb = String.format("%.1f KB", recordingState.estimatedBytes / 1024.0)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.errorContainer,
        border = BorderStroke(1.5.dp, scheme.error),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ptt_recording_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Recording live voice",
                tint = scheme.error,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("TX 0:%02d / 0:%02d", elapsedSec, maxSec),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = TelemetryMonoFontFamily),
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onErrorContainer
                    )
                    Text(
                        text = "${recordingState.codecLabel} • $estKb",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                }
                PttWaveformStrip(
                    levels = recordingState.liveLevels,
                    progressFraction = 1f,
                    activeColor = scheme.error,
                    inactiveColor = scheme.error.copy(alpha = 0.25f)
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(scheme.surface.copy(alpha = 0.85f))
                    .testTag("ptt_cancel_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel voice recording",
                    tint = scheme.onSurface
                )
            }
            Button(
                onClick = onStopAndSend,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.error,
                    contentColor = scheme.onError
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("ptt_send_now_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Send", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun VoiceMessageBubbleContent(
    message: DecryptedPackMessage,
    playbackState: PttPlaybackUiState,
    isMe: Boolean,
    onTogglePlay: (DecryptedPackMessage) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isPlaying = playbackState.playingMessageId == message.id
    val progress = if (isPlaying) playbackState.progressFraction else 0f
    val durationLabel = PttVoiceEngine.formatDurationSeconds(message.audioDurationMs)
    val codecBadge = PttVoiceEngine.formatCodecBadge(message.audioCodec, message.audioSizeBytes)
    val peaks = remember(message.id, message.audioPeaks) {
        if (message.audioPeaks.isNotEmpty()) message.audioPeaks else List(16) { idx -> if (idx % 2 == 0) 6 else 3 }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = { onTogglePlay(message) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isMe) scheme.primary else scheme.secondary)
                    .testTag("play_voice_${message.id}")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice message" else "Play voice message",
                    tint = if (isMe) scheme.onPrimary else scheme.onSecondary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PttWaveformStrip(
                    levels = peaks,
                    progressFraction = progress,
                    activeColor = if (isMe) scheme.primary else scheme.secondary,
                    inactiveColor = scheme.onSurfaceVariant.copy(alpha = 0.28f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPlaying) {
                            "${PttVoiceEngine.formatDurationSeconds(playbackState.currentPositionMs)} / $durationLabel"
                        } else {
                            durationLabel
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TelemetryMonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = codecBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun PttWaveformStrip(
    levels: List<Int>,
    progressFraction: Float,
    activeColor: androidx.compose.ui.graphics.Color,
    inactiveColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val safeLevels = if (levels.isEmpty()) List(16) { 3 } else levels
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        safeLevels.forEachIndexed { idx, level ->
            val barPos = (idx + 1).toFloat() / safeLevels.size.toFloat()
            val barColor = if (barPos <= progressFraction + 0.02f && progressFraction > 0f) {
                activeColor
            } else if (progressFraction == 0f) {
                activeColor.copy(alpha = 0.7f)
            } else {
                inactiveColor
            }
            val barHeightDp = (4 + (level.coerceIn(1, 9) * 2)).dp
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeightDp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun PttAutoPlayChip(
    enabled: Boolean,
    pendingVoiceCount: Int,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = enabled,
        onClick = { onToggle(!enabled) },
        leadingIcon = {
            Icon(
                imageVector = if (enabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (enabled) "Auto-play incoming voice enabled" else "Auto-play incoming voice muted",
                modifier = Modifier.size(16.dp)
            )
        },
        label = {
            Text(
                text = buildString {
                    append(if (enabled) "Auto-Play PTT" else "PTT Muted")
                    if (pendingVoiceCount > 0) append(" • $pendingVoiceCount queued")
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
        modifier = modifier.testTag("ptt_autoplay_chip")
    )
}
