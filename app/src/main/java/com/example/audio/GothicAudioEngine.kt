package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Real-time procedural gothic dark-ambient music & combat SFX synthesizer via Android AudioTrack.
 * Generates brooding Phrygian organ/cello sub-drones, haunting cathedral bells, and combat tension.
 */
class GothicAudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var bgmJob: Job? = null
    @Volatile private var bgmVolume: Float = 0.65f
    @Volatile private var sfxVolume: Float = 0.8f
    @Volatile private var combatIntensity: Float = 0.15f // 0.15 in sanctum, 0.75 in combat, 1.0 on boss

    private val sampleRate = 22050

    fun setVolumes(bgm: Float, sfx: Float) {
        bgmVolume = bgm.coerceIn(0f, 1f)
        sfxVolume = sfx.coerceIn(0f, 1f)
    }

    fun setCombatIntensity(intensity: Float) {
        combatIntensity = intensity.coerceIn(0f, 1f)
    }

    fun startMusic() {
        if (bgmJob?.isActive == true) return
        bgmJob = scope.launch {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@launch

            val chunkSamples = 2048
            val buffer = ShortArray(chunkSamples)
            // D Phrygian gothic progression frequencies (D2, Eb2, A2, Bb2, C3, D3, F3, A3)
            val bellNotes = doubleArrayOf(293.66, 311.13, 349.23, 440.0, 466.16, 261.63)
            var sampleIndex = 0L
            var nextBellSample = sampleRate.toLong() * 2L
            var bellEnv = 0.0
            var bellFreq = 293.66

            try {
                track.play()
                while (isActive) {
                    val vol = bgmVolume
                    val intensity = combatIntensity
                    for (i in 0 until chunkSamples) {
                        val t = sampleIndex.toDouble() / sampleRate.toDouble()
                        // Slow breathing sub-bass organ drone (D1 = 36.7Hz, D2 = 73.4Hz, A2 = 110Hz)
                        val lfo = 0.5 + 0.5 * sin(2.0 * PI * 0.11 * t)
                        val drone1 = sin(2.0 * PI * 73.42 * t) * 0.34
                        val drone2 = sin(2.0 * PI * 110.0 * t + 0.4 * sin(2.0 * PI * 0.23 * t)) * (0.20 + 0.12 * lfo)
                        val minorThird = sin(2.0 * PI * 87.31 * t) * (0.14 * lfo)
                        val fifth = sin(2.0 * PI * 146.83 * t) * (0.10 + 0.14 * intensity)

                        // Combat heartbeat pulse when inside dungeon
                        val pulseRate = 1.6 + intensity * 1.4
                        val pulseEnv = ((sin(2.0 * PI * pulseRate * t) + 1.0) * 0.5).let { it * it * it }
                        val warDrum = sin(2.0 * PI * 55.0 * t) * pulseEnv * intensity * 0.30

                        // Haunting cathedral bell overtones
                        if (sampleIndex >= nextBellSample) {
                            bellFreq = bellNotes[(sampleIndex % bellNotes.size).toInt()]
                            bellEnv = 0.42
                            nextBellSample = sampleIndex + (sampleRate * (3 + (sampleIndex % 3))).toLong()
                        }
                        val bell = (sin(2.0 * PI * bellFreq * t) + 0.4 * sin(2.0 * PI * bellFreq * 2.01 * t)) * bellEnv
                        bellEnv *= 0.99982

                        val mixed = (drone1 + drone2 + minorThird + fifth + warDrum + bell) * vol * 0.55
                        val clamped = mixed.coerceIn(-0.95, 0.95)
                        buffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
                        sampleIndex++
                    }
                    track.write(buffer, 0, chunkSamples)
                }
            } catch (_: Throwable) {
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    fun stopMusic() {
        bgmJob?.cancel()
        bgmJob = null
    }

    fun playSlashSfx() = playOneShot(durationMs = 110) { t, progress ->
        val freq = 380.0 - progress * 260.0
        val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.25
        (sin(2.0 * PI * freq * t) * 0.6 + noise) * (1.0 - progress)
    }

    fun playDashSfx() = playOneShot(durationMs = 140) { t, progress ->
        val freq = 180.0 + progress * 220.0
        val air = (Random.nextDouble() * 2.0 - 1.0) * 0.35
        (sin(2.0 * PI * freq * t) * 0.35 + air) * (1.0 - progress)
    }

    fun playHitSfx() = playOneShot(durationMs = 95) { t, progress ->
        val freq = 135.0 - progress * 75.0
        sin(2.0 * PI * freq * t) * (1.0 - progress * progress) * 0.85
    }

    fun playShrineChimeSfx() = playOneShot(durationMs = 340) { t, progress ->
        val f1 = sin(2.0 * PI * 440.0 * t)
        val f2 = sin(2.0 * PI * 659.25 * t) * 0.6
        (f1 + f2) * 0.5 * (1.0 - progress)
    }

    private fun playOneShot(durationMs: Int, generator: (t: Double, progress: Double) -> Double) {
        val vol = sfxVolume
        if (vol <= 0.02f) return
        scope.launch {
            val totalSamples = (sampleRate * durationMs) / 1000
            val samples = ShortArray(totalSamples)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate.toDouble()
                val progress = i.toDouble() / totalSamples.toDouble()
                val v = (generator(t, progress) * vol * 0.7).coerceIn(-0.95, 0.95)
                samples[i] = (v * Short.MAX_VALUE).toInt().toShort()
            }
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(totalSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull() ?: return@launch

            runCatching {
                track.write(samples, 0, totalSamples)
                track.play()
                kotlinx.coroutines.delay(durationMs.toLong() + 40L)
                track.stop()
                track.release()
            }
        }
    }
}
