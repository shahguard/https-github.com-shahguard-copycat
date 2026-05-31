package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceEchoEngine {
    private val TAG = "VoiceEchoEngine"
    private val sampleRate = 44100
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat) * 2

    private var audioRecord: AudioRecord? = null
    var isRecording = false
        private set
    var isPlaying = false
        private set

    // Emits the current mic volume (0.0 to 1.0) during recording
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    // Captures PCM audio data
    private var recordedData = ShortArray(0)

    @SuppressLint("MissingPermission")
    fun startRecording(onFinishedCallback: (ShortArray) -> Unit) {
        if (isRecording || isPlaying) return
        isRecording = true
        _amplitude.value = 0f

        CoroutineScope(Dispatchers.IO).launch {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfigIn,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord could not be initialized")
                    isRecording = false
                    return@launch
                }

                audioRecord?.startRecording()
                val tempBuffer = ShortArray(bufferSize)
                val capturedList = mutableListOf<Short>()
                val maxDurationSamples = sampleRate * 4 // Max 4 seconds of recording

                Log.d(TAG, "Recording started...")

                while (isRecording && capturedList.size < maxDurationSamples) {
                    val readShorts = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: 0
                    if (readShorts > 0) {
                        var sum = 0.0
                        for (i in 0 until readShorts) {
                            capturedList.add(tempBuffer[i])
                            sum += tempBuffer[i] * tempBuffer[i]
                        }
                        // Calculate RMS amplitude for UI feedback
                        val rms = Math.sqrt(sum / readShorts)
                        val norm = (rms / 32767.0).toFloat().coerceIn(0f, 1f)
                        _amplitude.value = norm
                    } else {
                        // Sleep slightly if nothing to read
                        kotlinx.coroutines.delay(10)
                    }
                }

                // Convert to ShortArray
                recordedData = capturedList.toShortArray()
                Log.d(TAG, "Recording stopped. Read ${recordedData.size} shorts.")
                withContext(Dispatchers.Main) {
                    onFinishedCallback(recordedData)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in startRecording: ${e.message}", e)
            } finally {
                cleanRecord()
            }
        }
    }

    fun stopRecording() {
        isRecording = false
    }

    private fun cleanRecord() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord = null
        _amplitude.value = 0f
    }

    fun playWithFilter(
        audioData: ShortArray, 
        pitchFactor: Float = 1.4f, 
        isRobot: Boolean = false, 
        onFinished: () -> Unit
    ) {
        if (isPlaying || audioData.isEmpty()) {
            onFinished()
            return
        }
        isPlaying = true

        CoroutineScope(Dispatchers.IO).launch {
            var audioTrack: AudioTrack? = null
            try {
                // Adjust the playback frequency on AudioTrack to shift pitch
                val playSampleRate = (sampleRate * pitchFactor).toInt().coerceIn(8000, 96000)
                
                audioTrack = AudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(playSampleRate)
                            .setChannelMask(channelConfigOut)
                            .build()
                    )
                    .setBufferSizeInBytes(audioData.size * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.play()

                // If Robot filter is activated, modify the samples to add tremolo/distortion
                val processedData = if (isRobot) {
                    ShortArray(audioData.size) { i ->
                        val sample = audioData[i]
                        // Robot voice effect: modulate with a sine wave
                        val freq = 45.0 // tremolo frequency in Hz
                        val t = i.toDouble() / sampleRate
                        val robotMod = Math.sin(2.0 * Math.PI * freq * t)
                        // Add slight metallic distortion
                        val modulated = (sample * (0.6 + 0.4 * robotMod)).toInt()
                        modulated.coerceIn(-32768, 32767).toShort()
                    }
                } else {
                    audioData
                }

                // Simple chunked playback to stream it smoothly
                val chunkSize = 2048
                var offset = 0
                while (offset < processedData.size && isPlaying) {
                    val count = Math.min(chunkSize, processedData.size - offset)
                    audioTrack.write(processedData, offset, count)
                    offset += count
                }

                // Small delay to ensure last chunk finishes playing
                kotlinx.coroutines.delay(200)

            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio with filter: ${e.message}", e)
            } finally {
                isPlaying = false
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (e: Exception) {
                    // ignore
                }
                withContext(Dispatchers.Main) {
                    onFinished()
                }
            }
        }
    }

    fun stopPlayback() {
        isPlaying = false
    }
}
