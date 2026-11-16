package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class PttRecordingUiState(
    val isRecording: Boolean = false,
    val elapsedMs: Int = 0,
    val maxDurationMs: Int = PttVoiceEngine.MAX_CLIP_DURATION_MS,
    val estimatedBytes: Int = 0,
    val codecLabel: String = "AMR 8kHz",
    val liveLevels: List<Int> = List(16) { 1 }
)

data class PttPlaybackUiState(
    val playingMessageId: String? = null,
    val progressFraction: Float = 0f,
    val currentPositionMs: Int = 0,
    val totalDurationMs: Int = 0
)

data class RecordedPttClip(
    val audioBase64: String,
    val audioCodec: String,
    val durationMs: Int,
    val sizeBytes: Int,
    val peaksCsv: String
)

class PttVoiceEngine(private val context: Context?) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _recordingState = MutableStateFlow(PttRecordingUiState())
    val recordingState: StateFlow<PttRecordingUiState> = _recordingState.asStateFlow()

    private val _playbackState = MutableStateFlow(PttPlaybackUiState())
    val playbackState: StateFlow<PttPlaybackUiState> = _playbackState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var activeRecordingFile: File? = null
    private var activeCodec: String = CODEC_AMR_NB
    private var recordingStartMs: Long = 0L
    private var meterJob: Job? = null
    private val collectedAmplitudes = mutableListOf<Int>()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackProgressJob: Job? = null

    fun startRecording(onMaxDurationReached: () -> Unit): Result<Unit> {
        val ctx = context?.applicationContext
            ?: return Result.failure(IllegalStateException("Audio context unavailable"))
        if (_recordingState.value.isRecording) return Result.success(Unit)
        stopPlayback()

        val cacheDir = File(ctx.cacheDir, "ptt").apply { mkdirs() }
        collectedAmplitudes.clear()

        // Try narrowband voice-optimized AMR_NB first (~1.5 KB/s), fallback to low-bitrate AAC (~2.0 KB/s)
        val attempt = startWithCodec(ctx, cacheDir, CODEC_AMR_NB)
            .recoverCatching { startWithCodec(ctx, cacheDir, CODEC_AAC_LOW).getOrThrow() }

        return attempt.onSuccess { (recorder, file, codec) ->
            mediaRecorder = recorder
            activeRecordingFile = file
            activeCodec = codec
            recordingStartMs = System.currentTimeMillis()
            val codecLabel = if (codec == CODEC_AMR_NB) "AMR 8kHz • 12kbps" else "AAC 16kHz • 16kbps"
            _recordingState.value = PttRecordingUiState(
                isRecording = true,
                elapsedMs = 0,
                codecLabel = codecLabel,
                liveLevels = List(16) { 1 }
            )
            meterJob?.cancel()
            meterJob = engineScope.launch {
                val bps = if (codec == CODEC_AMR_NB) AMR_BITRATE_BPS else AAC_BITRATE_BPS
                while (isActive && _recordingState.value.isRecording) {
                    val elapsed = (System.currentTimeMillis() - recordingStartMs).toInt().coerceAtLeast(0)
                    val rawAmp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val level = normalizeAmplitude(rawAmp)
                    collectedAmplitudes.add(level)
                    val recent = if (collectedAmplitudes.size >= 16) {
                        collectedAmplitudes.takeLast(16)
                    } else {
                        List(16 - collectedAmplitudes.size) { 1 } + collectedAmplitudes
                    }
                    val estBytes = ((elapsed.toLong() * bps) / 8000L).toInt().coerceAtLeast(1)
                    _recordingState.value = _recordingState.value.copy(
                        elapsedMs = elapsed,
                        estimatedBytes = estBytes,
                        liveLevels = recent
                    )
                    if (elapsed >= MAX_CLIP_DURATION_MS) {
                        onMaxDurationReached()
                        break
                    }
                    delay(85L)
                }
            }
        }.map { Unit }
    }

    private fun startWithCodec(
        ctx: Context,
        cacheDir: File,
        codec: String
    ): Result<Triple<MediaRecorder, File, String>> = runCatching {
        val ext = if (codec == CODEC_AMR_NB) "amr" else "m4a"
        val outFile = File(cacheDir, "rec_${System.currentTimeMillis()}.$ext")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(ctx)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            // VOICE_COMMUNICATION enables hardware acoustic echo cancellation & wind/noise suppression on Android
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            } catch (_: Exception) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            if (codec == CODEC_AMR_NB) {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                recorder.setAudioSamplingRate(8000)
                recorder.setAudioChannels(1)
                recorder.setAudioEncodingBitRate(AMR_BITRATE_BPS)
            } else {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioSamplingRate(16000)
                recorder.setAudioChannels(1)
                recorder.setAudioEncodingBitRate(AAC_BITRATE_BPS)
            }
            recorder.setMaxDuration(MAX_CLIP_DURATION_MS)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()
            recorder.start()
            Triple(recorder, outFile, codec)
        } catch (t: Throwable) {
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
            runCatching { outFile.delete() }
            throw t
        }
    }

    suspend fun stopRecordingAndEncode(): Result<RecordedPttClip> = withContext(Dispatchers.IO) {
        meterJob?.cancel()
        meterJob = null
        val recorder = mediaRecorder
        val file = activeRecordingFile
        val codec = activeCodec
        val elapsedMs = (System.currentTimeMillis() - recordingStartMs).toInt().coerceAtLeast(0)

        mediaRecorder = null
        activeRecordingFile = null
        _recordingState.value = PttRecordingUiState()

        if (recorder == null || file == null) {
            return@withContext Result.failure(IllegalStateException("No active PTT recording"))
        }

        val stopRes = runCatching {
            recorder.stop()
        }
        runCatching { recorder.reset() }
        runCatching { recorder.release() }

        if (stopRes.isFailure || !file.exists()) {
            runCatching { file.delete() }
            return@withContext Result.failure(
                stopRes.exceptionOrNull() ?: IllegalStateException("Recording capture failed")
            )
        }

        val bytes = runCatching { file.readBytes() }.getOrElse { ByteArray(0) }
        runCatching { file.delete() }

        if (elapsedMs < MIN_CLIP_DURATION_MS || bytes.size < 64) {
            return@withContext Result.failure(IllegalArgumentException("Hold mic longer to send voice callout"))
        }

        if (bytes.size > MAX_RAW_AUDIO_BYTES) {
            return@withContext Result.failure(IllegalStateException("Voice clip exceeded low-bandwidth 44 KB limit"))
        }

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val peaks = downsamplePeaks(collectedAmplitudes, 16)
        Result.success(
            RecordedPttClip(
                audioBase64 = base64,
                audioCodec = codec,
                durationMs = elapsedMs.coerceIn(MIN_CLIP_DURATION_MS, MAX_CLIP_DURATION_MS),
                sizeBytes = bytes.size,
                peaksCsv = peaks.joinToString(",")
            )
        )
    }

    fun cancelRecording() {
        meterJob?.cancel()
        meterJob = null
        val recorder = mediaRecorder
        val file = activeRecordingFile
        mediaRecorder = null
        activeRecordingFile = null
        _recordingState.value = PttRecordingUiState()
        runCatching { recorder?.stop() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        runCatching { file?.delete() }
    }

    fun togglePlayback(
        messageId: String,
        audioBase64: String,
        codec: String,
        fallbackDurationMs: Int
    ): Result<Unit> {
        val current = _playbackState.value
        if (current.playingMessageId == messageId) {
            stopPlayback()
            return Result.success(Unit)
        }

        val ctx = context?.applicationContext
            ?: return Result.failure(IllegalStateException("Audio playback context unavailable"))
        if (audioBase64.isBlank()) {
            return Result.failure(IllegalArgumentException("Voice clip payload empty"))
        }

        stopPlayback()

        return runCatching {
            val cacheDir = File(ctx.cacheDir, "ptt").apply { mkdirs() }
            val ext = if (codec == CODEC_AAC_LOW) "m4a" else "amr"
            val safeName = messageId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val audioFile = File(cacheDir, "play_$safeName.$ext")
            if (!audioFile.exists() || audioFile.length() == 0L) {
                val decoded = Base64.decode(audioBase64, Base64.DEFAULT)
                audioFile.writeBytes(decoded)
            }

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            val duration = player.duration.takeIf { it > 0 } ?: fallbackDurationMs.coerceAtLeast(1000)
            _playbackState.value = PttPlaybackUiState(
                playingMessageId = messageId,
                progressFraction = 0f,
                currentPositionMs = 0,
                totalDurationMs = duration
            )

            playbackProgressJob = engineScope.launch {
                while (isActive && _playbackState.value.playingMessageId == messageId) {
                    val activePlayer = mediaPlayer ?: break
                    val pos = runCatching { activePlayer.currentPosition }.getOrDefault(0)
                    val total = runCatching { activePlayer.duration }.getOrDefault(duration).coerceAtLeast(1)
                    val frac = (pos.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    _playbackState.value = PttPlaybackUiState(
                        playingMessageId = messageId,
                        progressFraction = frac,
                        currentPositionMs = pos,
                        totalDurationMs = total
                    )
                    delay(80L)
                }
            }
        }.onFailure {
            stopPlayback()
        }.map { Unit }
    }

    fun stopPlayback() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
        val player = mediaPlayer
        mediaPlayer = null
        _playbackState.value = PttPlaybackUiState()
        runCatching {
            if (player?.isPlaying == true) {
                player.stop()
            }
        }
        runCatching { player?.release() }
    }

    fun release() {
        cancelRecording()
        stopPlayback()
        engineScope.cancel()
    }

    companion object {
        const val CODEC_AMR_NB = "AMR_NB"
        const val CODEC_AAC_LOW = "AAC_LOW"
        const val AMR_BITRATE_BPS = 12_200
        const val AAC_BITRATE_BPS = 16_000
        const val MIN_CLIP_DURATION_MS = 350
        const val MAX_CLIP_DURATION_MS = 20_000
        const val MAX_RAW_AUDIO_BYTES = 46_000

        fun normalizeAmplitude(rawMaxAmplitude: Int): Int {
            if (rawMaxAmplitude <= 120) return 1
            val ratio = (rawMaxAmplitude / 24000.0).coerceIn(0.0, 1.0)
            return (1 + (ratio * 8.0).roundToInt()).coerceIn(1, 9)
        }

        fun downsamplePeaks(samples: List<Int>, targetBars: Int = 16): List<Int> {
            if (samples.isEmpty()) return List(targetBars) { 2 }
            if (samples.size <= targetBars) {
                val pad = targetBars - samples.size
                return samples + List(pad) { samples.last().coerceIn(1, 9) }
            }
            val bucketSize = samples.size.toDouble() / targetBars
            return List(targetBars) { idx ->
                val start = (idx * bucketSize).toInt().coerceIn(0, samples.lastIndex)
                val end = (((idx + 1) * bucketSize).toInt()).coerceIn(start + 1, samples.size)
                var peak = 1
                for (i in start until end) {
                    if (samples[i] > peak) peak = samples[i]
                }
                peak.coerceIn(1, 9)
            }
        }

        fun parsePeaksCsv(csv: String): List<Int> {
            if (csv.isBlank()) return List(16) { idx -> if (idx % 3 == 0) 6 else 3 }
            val parsed = csv.split(",")
                .mapNotNull { it.trim().toIntOrNull()?.coerceIn(1, 9) }
            return if (parsed.isEmpty()) List(16) { 3 } else downsamplePeaks(parsed, 16)
        }

        fun formatDurationSeconds(durationMs: Int): String {
            val totalSec = ((durationMs + 500) / 1000).coerceAtLeast(1)
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%d:%02d", min, sec)
        }

        fun formatCodecBadge(codec: String, sizeBytes: Int): String {
            val kb = String.format("%.1f KB", sizeBytes / 1024.0)
            val shortCodec = when (codec) {
                CODEC_AMR_NB -> "AMR 8kHz"
                CODEC_AAC_LOW -> "AAC 16kHz"
                else -> "Voice"
            }
            return "$shortCodec • $kb"
        }
    }
}
