package com.cherryblossomdev.breakroom.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.random.Random

// Continuous synthesized engine roar for the Haulonaut landing/launch sequences --
// filtered white noise plus a low sawtooth tone, ported from web's Web Audio graph
// (frontend/src/utilities/haulonautSound.js's startHaulonautEngineRoar and friends) onto
// AudioTrack MODE_STREAM, since Android has no Web-Audio-style node graph. Replaces the
// old two-disconnected-one-shot-bursts approach (Sfx.DESCENT/ENTRY/LAUNCH): this is
// audible continuously from start() to stop(), with intensity (loudness + filter
// brightness, moving together so "louder" reads as "closer") driven by rampIntensity()/
// spike() the same way callers drive the Web Audio version.
//
// Approximates web's 2nd-order resonant lowpass (BiquadFilterNode, Q=0.7) with a simple
// one-pole RC lowpass -- cheaper to run per-sample on a phone CPU and close enough by ear
// for a background engine bed; there's no resonant peak, but the brightening-with-cutoff
// behavior callers actually rely on (rampIntensity moving the filter frequency) still
// comes through.
object HaulonautEngineRoar {
    private const val SAMPLE_RATE = 44100
    private const val BUFFER_FRAMES = 1024
    private const val BASE_FILTER_FREQ = 500f
    private const val BASE_INTENSITY = 0.08f
    private const val BASE_OSC_FREQ = 55f

    // A single linearly-interpolated ramp, mirroring a Web Audio AudioParam's
    // cancelScheduledValues + setValueAtTime(current) + linearRampToValueAtTime pattern:
    // retargeting mid-ramp starts from wherever the interpolation currently is, not from
    // the old target.
    private class Ramp(base: Float) {
        @Volatile var from: Float = base
        @Volatile var to: Float = base
        @Volatile var startNanos: Long = 0L
        @Volatile var durationNanos: Long = 1L

        fun valueAt(nowNanos: Long): Float {
            if (durationNanos <= 0L) return to
            val t = ((nowNanos - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
            return from + (to - from) * t
        }

        fun retarget(target: Float, durationMs: Long, nowNanos: Long) {
            from = valueAt(nowNanos)
            to = target
            startNanos = nowNanos
            durationNanos = (durationMs * 1_000_000L).coerceAtLeast(1L)
        }

        fun reset(base: Float, nowNanos: Long) {
            from = base
            to = base
            startNanos = nowNanos
            durationNanos = 1L
        }
    }

    private val intensityRamp = Ramp(BASE_INTENSITY)
    private val filterFreqRamp = Ramp(BASE_FILTER_FREQ)
    private val oscGainRamp = Ramp(0f)
    private val oscFreqRamp = Ramp(BASE_OSC_FREQ)

    private var audioTrack: AudioTrack? = null
    private var renderThread: Thread? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSizeBytes = maxOf(minBuf * 2, BUFFER_FRAMES * 2 * 2)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            return
        }

        val now = System.nanoTime()
        intensityRamp.reset(BASE_INTENSITY, now)
        filterFreqRamp.reset(BASE_FILTER_FREQ, now)
        oscGainRamp.reset(0f, now)
        oscFreqRamp.reset(BASE_OSC_FREQ, now)

        audioTrack = track
        running = true
        track.play()
        renderThread = Thread({ renderLoop(track) }, "HaulonautEngineRoar").apply { start() }
    }

    // Ramps loudness + filter brightness + engine pitch/body together toward `target`
    // (0..1) over `durationMs` -- "louder" reads as "closer" (or "farther" as target
    // drops), not just a volume change. No-op if start() hasn't been called.
    fun rampIntensity(target: Float, durationMs: Long) {
        if (!running) return
        val clamped = target.coerceIn(0f, 1f)
        val now = System.nanoTime()
        intensityRamp.retarget(BASE_INTENSITY + clamped * 0.55f, durationMs, now)
        filterFreqRamp.retarget(BASE_FILTER_FREQ + clamped * 2600f, durationMs, now)
        oscGainRamp.retarget(clamped * 0.5f, durationMs, now)
        oscFreqRamp.retarget(BASE_OSC_FREQ + clamped * 70f, durationMs, now)
    }

    // The flame beat: a fast 150ms attack to a hot peak, timed by the caller to land right
    // as flames appear on screen (atmospheric entry going down, ignition going up).
    fun spike() {
        if (!running) return
        val now = System.nanoTime()
        val attackMs = 150L
        intensityRamp.retarget(1.0f, attackMs, now)
        filterFreqRamp.retarget(3400f, attackMs, now)
        oscGainRamp.retarget(0.7f, attackMs, now)
    }

    // Fades to silence over `fadeMs` and tears down the AudioTrack. Safe to call when
    // nothing is running.
    fun stop(fadeMs: Long = 400) {
        if (!running) return
        val now = System.nanoTime()
        intensityRamp.retarget(0f, fadeMs, now)
        val track = audioTrack
        val thread = renderThread
        Thread {
            Thread.sleep(fadeMs + 50)
            running = false
            try { thread?.join(500) } catch (_: InterruptedException) {}
            try {
                track?.stop()
            } catch (_: Exception) {
            }
            track?.release()
            if (audioTrack === track) audioTrack = null
        }.start()
    }

    private fun renderLoop(track: AudioTrack) {
        val buffer = ShortArray(BUFFER_FRAMES)
        val noiseRandom = Random(System.nanoTime())
        var filterState = 0f
        var oscPhase = 0f
        val dt = 1f / SAMPLE_RATE

        while (running) {
            val now = System.nanoTime()
            val intensity = intensityRamp.valueAt(now)
            val filterFreq = filterFreqRamp.valueAt(now)
            val oscGain = oscGainRamp.valueAt(now)
            val oscFreq = oscFreqRamp.valueAt(now)
            val userVolume = if (HaulonautSoundService.soundMuted) 0f else HaulonautSoundService.soundVolume

            // One-pole RC lowpass coefficient for this buffer -- recomputed once per
            // buffer (not per sample), which is cheap enough at ~23ms/buffer and smooth
            // enough for ramps that move over hundreds of ms.
            val rc = 1f / (2f * PI.toFloat() * filterFreq.coerceAtLeast(20f))
            val alpha = dt / (rc + dt)
            val oscPhaseInc = oscFreq / SAMPLE_RATE

            for (i in buffer.indices) {
                val noise = noiseRandom.nextFloat() * 2f - 1f
                filterState += alpha * (noise - filterState)

                oscPhase += oscPhaseInc
                if (oscPhase >= 1f) oscPhase -= 1f
                val saw = (oscPhase * 2f - 1f) * oscGain

                val mixed = (filterState + saw) * intensity * userVolume
                buffer[i] = (mixed.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }

            track.write(buffer, 0, buffer.size)
        }
    }
}
