package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.audio.PttPlaybackUiState
import com.example.audio.PttRecordingUiState
import com.example.audio.PttVoiceEngine
import com.example.data.crypto.PackCrypto
import com.example.data.model.DecryptedPackMessage
import com.example.ui.theme.HudThemeMode
import com.example.ui.theme.MotoPackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class PttVoiceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pttVoiceEngine_helpers_downsampleNormalizeAndFormatLowBandwidthBadges() {
        assertEquals(1, PttVoiceEngine.normalizeAmplitude(0))
        assertEquals(9, PttVoiceEngine.normalizeAmplitude(28_000))

        val downsampled = PttVoiceEngine.downsamplePeaks(listOf(2, 8, 3, 9, 4, 7, 1, 5), targetBars = 4)
        assertEquals(listOf(8, 9, 7, 5), downsampled)

        val parsed = PttVoiceEngine.parsePeaksCsv("2,6,9,4")
        assertEquals(16, parsed.size)
        assertEquals("0:06", PttVoiceEngine.formatDurationSeconds(6100))
        assertEquals("AMR 8kHz • 9.0 KB", PttVoiceEngine.formatCodecBadge(PttVoiceEngine.CODEC_AMR_NB, 9216))
        assertEquals("AAC 16kHz • 4.0 KB", PttVoiceEngine.formatCodecBadge(PttVoiceEngine.CODEC_AAC_LOW, 4096))
    }

    @Test
    fun packCrypto_encryptsAndDecryptsLowBandwidthVoicePayloadCleanly() {
        val key = PackCrypto.derivePackKey("PEAK99", "salt_ptt_roundtrip_123")
        val rawVoiceBase64 = "IWFNTQpyn4m7v8X9P+2QwA==".repeat(20)
        val encrypted = PackCrypto.encryptString(rawVoiceBase64, key)
        val decrypted = PackCrypto.decryptString(encrypted, key)
        assertEquals(rawVoiceBase64, decrypted)
        assertTrue(encrypted.length < 65000)
    }

    @Test
    fun voiceMessageBubble_rendersDurationCodecAndPlayCallback() {
        var playedId: String? = null
        val voiceMsg = DecryptedPackMessage(
            id = "voice_msg_1",
            senderId = "bob_uid",
            senderName = "Bob Tenere",
            recipientId = "",
            text = "Voice callout (6s • 9.0 KB)",
            messageType = "voice",
            timestampMs = System.currentTimeMillis(),
            audioBase64 = "SAMPLE_AMR_DATA",
            audioCodec = PttVoiceEngine.CODEC_AMR_NB,
            audioDurationMs = 6200,
            audioSizeBytes = 9216,
            audioPeaks = listOf(2, 4, 7, 9, 6, 3, 5, 8, 7, 4, 3, 6, 8, 5, 3, 2)
        )

        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.NIGHT_HUD) {
                VoiceMessageBubbleContent(
                    message = voiceMsg,
                    playbackState = PttPlaybackUiState(),
                    isMe = false,
                    onTogglePlay = { playedId = it.id }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("0:06").assertIsDisplayed()
        composeTestRule.onNodeWithText("AMR 8kHz • 9.0 KB").assertIsDisplayed()
        composeTestRule.onNodeWithTag("play_voice_voice_msg_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("voice_msg_1", playedId)
    }

    @Test
    fun pttRecordingBannerAndAutoPlayChip_triggerSendCancelAndToggleCallbacks() {
        var sentCount = 0
        var cancelCount = 0
        var autoPlayEnabled = true

        composeTestRule.setContent {
            MotoPackTheme(hudThemeMode = HudThemeMode.DAYLIGHT) {
                androidx.compose.foundation.layout.Column {
                    PttAutoPlayChip(
                        enabled = autoPlayEnabled,
                        pendingVoiceCount = 2,
                        onToggle = { autoPlayEnabled = it }
                    )
                    PttLiveRecordingBanner(
                        recordingState = PttRecordingUiState(
                            isRecording = true,
                            elapsedMs = 4200,
                            maxDurationMs = 20000,
                            estimatedBytes = 6400,
                            codecLabel = "AMR 8kHz • 12kbps",
                            liveLevels = listOf(2, 5, 7, 9, 6, 4, 3, 6, 8, 5, 3, 4, 7, 8, 5, 3)
                        ),
                        onStopAndSend = { sentCount++ },
                        onCancel = { cancelCount++ }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ptt_recording_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ptt_autoplay_chip").assertTextContains("2 queued", substring = true)
        composeTestRule.onNodeWithTag("ptt_send_now_button").performClick()
        composeTestRule.onNodeWithTag("ptt_cancel_button").performClick()
        composeTestRule.onNodeWithTag("ptt_autoplay_chip").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, sentCount)
        assertEquals(1, cancelCount)
        assertEquals(false, autoPlayEnabled)
    }
}
