package com.example.ui.screens

import android.app.Activity
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.SmartHudUiState
import com.example.audio.PttRecordingUiState
import com.example.data.model.DecryptedRiderState
import com.example.data.model.RidePack
import com.example.location.LiveGpsFix
import com.example.ui.theme.HudThemeMode
import com.example.ui.viewmodel.HudNavigationUiState
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

@Composable
fun RiderHudScreen(
    currentUserId: String,
    activePack: RidePack?,
    riders: List<DecryptedRiderState>,
    myGpsFix: LiveGpsFix,
    batteryPercent: Int,
    ecoRideMode: Boolean,
    hudThemeMode: HudThemeMode,
    isEffectiveOffline: Boolean,
    isPlanningRoute: Boolean,
    hudNav: HudNavigationUiState,
    smartHudState: SmartHudUiState,
    hudVoiceEnabled: Boolean,
    isSosActive: Boolean,
    sosLastBroadcastMs: Long?,
    pttRecordingState: PttRecordingUiState = PttRecordingUiState(),
    onStartPttRecording: () -> Unit = {},
    onStopAndSendPttRecording: () -> Unit = {},
    onCancelPttRecording: () -> Unit = {},
    onToggleVoice: (Boolean) -> Unit,
    onToggleEcoMode: (Boolean) -> Unit,
    onSetHudThemeMode: (HudThemeMode) -> Unit,
    onStepDelta: (Int) -> Unit,
    onResetAutoStep: () -> Unit,
    onQuickPlanPreset: (routeName: String, destinationLabel: String, destLatLng: LatLng?) -> Unit,
    onRouteToPackLeader: () -> Unit,
    onOpenRoutePlanner: () -> Unit,
    onRunSmartScan: () -> Unit,
    onTriggerSos: (String) -> Unit,
    onStandDownSos: () -> Unit,
    onExitHud: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val isNightHud = scheme.background.luminance() < 0.3f

    var showSosSheet by remember { mutableStateOf(false) }
    var lastSpokenStepKey by remember { mutableStateOf<String?>(null) }
    var lastSpokenAiHeadline by remember { mutableStateOf<String?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    BackHandler(onBack = onExitHud)

    // Keep bike-mounted screen awake while Rider HUD is visible
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // On-device TextToSpeech engine for hands-free helmet intercom turn & hazard announcements
    val tts = remember(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.getDefault()
                ttsReady = true
            }
        }
        instance
    }

    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun speakAnnouncement(text: String) {
        if (!ttsReady || !hudVoiceEnabled || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hud_${System.currentTimeMillis()}")
    }

    // Automatic turn-by-turn voice cue when advancing steps or entering final 450m / 120m approach window
    LaunchedEffect(hudNav.hasActiveRoute, hudNav.currentStepIndex, hudNav.distanceToNextTurnMeters, hudVoiceEnabled, ttsReady) {
        if (!hudNav.hasActiveRoute || !hudVoiceEnabled || !ttsReady) return@LaunchedEffect
        val bucket = when {
            hudNav.distanceToNextTurnMeters <= 120 -> "FINAL"
            hudNav.distanceToNextTurnMeters <= 450 -> "APPROACH"
            else -> "STEP"
        }
        val key = "${hudNav.currentStepIndex}_$bucket"
        if (lastSpokenStepKey != key) {
            lastSpokenStepKey = key
            val distPhrase = formatHudDistance(hudNav.distanceToNextTurnMeters)
            speakAnnouncement("In $distPhrase, ${hudNav.currentInstruction}")
        }
    }

    // Automatic voice readout for freshly generated Gemini Smart HUD callouts
    LaunchedEffect(smartHudState, hudVoiceEnabled, ttsReady) {
        if (smartHudState is SmartHudUiState.Success && hudVoiceEnabled && ttsReady) {
            val headline = smartHudState.callout.headline
            if (lastSpokenAiHeadline != headline) {
                lastSpokenAiHeadline = headline
                speakAnnouncement(smartHudState.callout.spokenTip)
            }
        }
    }

    val otherRidersExist = remember(riders, currentUserId) {
        riders.any { it.userId != currentUserId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("rider_hud_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // High-Contrast Cockpit Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hudNav.hasActiveRoute) hudNav.routeName.uppercase(Locale.US) else "MOTO RIDER HUD",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(activePack?.name ?: "Solo Cockpit")
                        if (isEffectiveOffline) append(" • OFFLINE")
                        if (hudVoiceEnabled) append(" • VOICE ON") else append(" • MUTED")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Night Cockpit <-> High-Vis Sunlight toggle
                IconButton(
                    onClick = {
                        onSetHudThemeMode(if (isNightHud) HudThemeMode.DAYLIGHT else HudThemeMode.NIGHT_HUD)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant)
                        .testTag("hud_contrast_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isNightHud) Icons.Default.BrightnessHigh else Icons.Default.NightlightRound,
                        contentDescription = if (isNightHud) "Switch to Daylight High-Vis" else "Switch to Night Cockpit",
                        tint = scheme.primary
                    )
                }

                // Voice Intercom Turn Cues toggle
                IconButton(
                    onClick = { onToggleVoice(!hudVoiceEnabled) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (hudVoiceEnabled) scheme.secondary.copy(alpha = 0.22f) else scheme.surfaceVariant)
                        .testTag("hud_voice_toggle_button")
                ) {
                    Icon(
                        imageVector = if (hudVoiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (hudVoiceEnabled) "Mute voice cues" else "Enable voice cues",
                        tint = if (hudVoiceEnabled) scheme.secondary else scheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onExitHud,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant)
                        .testTag("hud_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Rider HUD",
                        tint = scheme.onSurface
                    )
                }
            }
        }

        // Main Cockpit Body (adaptive for bar-mounted portrait vs landscape/tablet)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val useTwoPane = maxWidth >= 600.dp
            if (useTwoPane) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (hudNav.hasActiveRoute) {
                            RiderHudManeuverHeroCard(
                                hudNav = hudNav,
                                onStepDelta = onStepDelta,
                                onResetAutoStep = onResetAutoStep
                            )
                        } else {
                            RiderHudFreeRideCard(
                                myGpsFix = myGpsFix,
                                hasPackRiders = otherRidersExist,
                                isPlanningRoute = isPlanningRoute,
                                onQuickPlanPreset = onQuickPlanPreset,
                                onRouteToPackLeader = onRouteToPackLeader,
                                onOpenRoutePlanner = onOpenRoutePlanner
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RiderHudTelemetryGrid(
                            myGpsFix = myGpsFix,
                            hudNav = hudNav,
                            batteryPercent = batteryPercent,
                            ecoRideMode = ecoRideMode,
                            onToggleEcoMode = onToggleEcoMode
                        )
                        RiderHudSmartCard(
                            hudNav = hudNav,
                            smartHudState = smartHudState,
                            isEffectiveOffline = isEffectiveOffline,
                            onRunSmartScan = onRunSmartScan,
                            onSpeakCallout = { text -> speakAnnouncement(text) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hudNav.hasActiveRoute) {
                        RiderHudManeuverHeroCard(
                            hudNav = hudNav,
                            onStepDelta = onStepDelta,
                            onResetAutoStep = onResetAutoStep
                        )
                    } else {
                        RiderHudFreeRideCard(
                            myGpsFix = myGpsFix,
                            hasPackRiders = otherRidersExist,
                            isPlanningRoute = isPlanningRoute,
                            onQuickPlanPreset = onQuickPlanPreset,
                            onRouteToPackLeader = onRouteToPackLeader,
                            onOpenRoutePlanner = onOpenRoutePlanner
                        )
                    }

                    RiderHudTelemetryGrid(
                        myGpsFix = myGpsFix,
                        hudNav = hudNav,
                        batteryPercent = batteryPercent,
                        ecoRideMode = ecoRideMode,
                        onToggleEcoMode = onToggleEcoMode
                    )

                    RiderHudSmartCard(
                        hudNav = hudNav,
                        smartHudState = smartHudState,
                        isEffectiveOffline = isEffectiveOffline,
                        onRunSmartScan = onRunSmartScan,
                        onSpeakCallout = { text -> speakAnnouncement(text) }
                    )
                }
            }
        }

        PttLiveRecordingBanner(
            recordingState = pttRecordingState,
            onStopAndSend = onStopAndSendPttRecording,
            onCancel = onCancelPttRecording
        )

        // Oversized Glove-Friendly Bottom Action Dock (>= 54.dp height targets)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onExitHud,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, scheme.outline),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("hud_exit_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Map", fontWeight = FontWeight.Bold, maxLines = 1)
            }

            if (activePack != null) {
                PttHoldOrTapButton(
                    isRecording = pttRecordingState.isRecording,
                    compactLabel = true,
                    testTag = "hud_ptt_button",
                    onStartRecording = onStartPttRecording,
                    onStopAndSendRecording = onStopAndSendPttRecording
                )
            }

            Button(
                onClick = { showSosSheet = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.error,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("hud_sos_button")
            ) {
                Icon(Icons.Default.Sos, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSosActive) "SOS ACTIVE" else "SOS",
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }

    if (showSosSheet) {
        EmergencySosBottomSheet(
            isSosActive = isSosActive,
            sosLastBroadcastMs = sosLastBroadcastMs,
            activePack = activePack,
            currentUserId = currentUserId,
            riders = riders,
            myGpsFix = myGpsFix,
            batteryPercent = batteryPercent,
            isEffectiveOffline = isEffectiveOffline,
            onTriggerSos = onTriggerSos,
            onStandDownSos = onStandDownSos,
            onDismiss = { showSosSheet = false }
        )
    }
}
