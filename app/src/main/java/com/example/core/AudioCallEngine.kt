package com.example.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Real Android AudioRecord microphone amplitude sampler + AudioManager speakerphone controller
 * used during active E2EE voice calls and voice note capture.
 */
class AudioCallEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _micLevel = MutableStateFlow(0.08f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun startAudioCapture(scope: CoroutineScope, hasMicPermission: Boolean) {
        stopAudioCapture()
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isMicrophoneMute = _isMuted.value
            audioManager?.isSpeakerphoneOn = _isSpeakerOn.value
        } catch (e: Exception) {
            Log.w(TAG, "AudioManager mode setup skipped: ${e.message}")
        }

        if (!hasMicPermission) {
            _micLevel.value = 0.05f
            return
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                Log.w(TAG, "Invalid AudioRecord buffer size: $minBuf")
                return@launch
            }
            try {
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    return@launch
                }
                audioRecord = recorder
                recorder.startRecording()
                val buffer = ShortArray(minBuf / 2)
                while (isActive) {
                    if (_isMuted.value) {
                        _micLevel.value = 0.03f
                        delay(80)
                        continue
                    }
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            val s = buffer[i].toDouble()
                            sumSquares += s * s
                        }
                        val rms = sqrt(sumSquares / read)
                        val normalized = (rms / 8000.0).toFloat().coerceIn(0.06f, 1.0f)
                        _micLevel.value = normalized
                    }
                    delay(60)
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord capture error: ${e.message}")
            }
        }
    }

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        try {
            audioManager?.isMicrophoneMute = next
        } catch (_: Exception) {
        }
    }

    fun toggleSpeaker() {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        try {
            audioManager?.isSpeakerphoneOn = next
        } catch (_: Exception) {
        }
    }

    fun stopAudioCapture() {
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        try {
            audioManager?.isMicrophoneMute = false
            audioManager?.isSpeakerphoneOn = false
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
        _micLevel.value = 0.05f
    }

    private companion object {
        const val TAG = "AudioCallEngine"
    }
}
