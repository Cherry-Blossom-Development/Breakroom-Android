package com.cherryblossomdev.breakroom.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.core.content.edit
import com.cherryblossomdev.breakroom.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Haulonaut sound effects. Process-wide singleton (one SoundPool, one ambient MediaPlayer,
// one set of mute/volume prefs) rather than something the ViewModel owns, mirroring web's
// haulonautSound.js module-singleton and iPhone's HaulonautSoundService enum. SFX and
// ambient are two independent buses -- separate mute/volume -- matching web (iPhone
// collapses them into one control; web's split is the more deliberate design and there's
// room for two controls in the Android UI).
object HaulonautSoundService {

    enum class Sfx(val resId: Int) {
        CLICK(R.raw.haulonaut_ui_click),
        OPEN(R.raw.haulonaut_ui_open),
        SUCCESS(R.raw.haulonaut_ui_success),
        ERROR(R.raw.haulonaut_ui_error),
        WARP(R.raw.haulonaut_warp),
        DRIFT(R.raw.haulonaut_drift),
        ARRIVAL(R.raw.haulonaut_arrival),
        PRESENCE(R.raw.haulonaut_presence),
        HIT(R.raw.haulonaut_hit),
        DAMAGE(R.raw.haulonaut_damage),
        DANGER(R.raw.haulonaut_danger),
        DEATH(R.raw.haulonaut_death),
        NOTIFY(R.raw.haulonaut_notify),
        TRADE_SUCCESS(R.raw.haulonaut_trade_success),
        TRADE_DECLINE(R.raw.haulonaut_trade_decline),
        // DESCENT/ENTRY/LAUNCH retired in favor of the continuous synthesized bed in
        // HaulonautEngineRoar (see beginLanding()/launch() in HaulonautPlayScreen.kt) --
        // the two-disconnected-one-shot-bursts problem web fixed the same way.
        DOCK(R.raw.haulonaut_dock),
        BUGGY_MOVE(R.raw.haulonaut_buggy_move),
        NPC_PRESENCE(R.raw.haulonaut_npc_presence),
        LANDING_EVENT(R.raw.haulonaut_landing_event)
    }

    enum class Ambient(val resId: Int) {
        SPACE(R.raw.haulonaut_amb_space),
        OUTPOST(R.raw.haulonaut_amb_outpost),
        SURFACE(R.raw.haulonaut_amb_surface)
    }

    private const val PREFS_NAME = "haulonaut_sound_prefs"
    private const val KEY_MUTED = "muted"
    private const val KEY_VOLUME = "volume"
    private const val KEY_AMBIENT_MUTED = "ambient_muted"
    private const val KEY_AMBIENT_VOLUME = "ambient_volume"

    // Same defaults as web, so the two clients feel the same out of the box.
    private const val DEFAULT_VOLUME = 0.6f
    private const val DEFAULT_AMBIENT_VOLUME = 0.35f
    private const val AMBIENT_FADE_MS = 800L
    private const val FADE_STEP_MS = 40L

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<Sfx, Int>()

    // The ambient bed currently playing (or fading in) -- separate from `outgoingPlayer`,
    // the previous bed fading out, so both can run at once for a crossfade like web's.
    private var activePlayer: MediaPlayer? = null
    private var activeLevel = 0f
    private var activeFadeJob: Job? = null
    private var outgoingPlayer: MediaPlayer? = null
    private var outgoingFadeJob: Job? = null

    // Which ambient bed the caller last asked for, independent of ambientMuted -- muting
    // pauses playback but remembers the key, so unmuting resumes the right bed instead of
    // needing the caller to re-request it. Mirrors web's currentAmbientKey.
    private var currentAmbient: Ambient? = null

    var soundMuted: Boolean
        get() = prefs?.getBoolean(KEY_MUTED, false) ?: false
        set(value) { prefs?.edit { putBoolean(KEY_MUTED, value) } }

    var soundVolume: Float
        get() = prefs?.getFloat(KEY_VOLUME, DEFAULT_VOLUME) ?: DEFAULT_VOLUME
        set(value) { prefs?.edit { putFloat(KEY_VOLUME, value.coerceIn(0f, 1f)) } }

    // Ambience defaults to on (unmuted) -- a deliberate per-game choice, matching web,
    // not just "whatever the SFX mute default happens to be".
    var ambientMuted: Boolean
        get() = prefs?.getBoolean(KEY_AMBIENT_MUTED, false) ?: false
        set(value) {
            prefs?.edit { putBoolean(KEY_AMBIENT_MUTED, value) }
            val key = currentAmbient
            if (value) {
                retireActivePlayer()
            } else if (key != null) {
                beginActivePlayer(key)
            }
        }

    var ambientVolume: Float
        get() = prefs?.getFloat(KEY_AMBIENT_VOLUME, DEFAULT_AMBIENT_VOLUME) ?: DEFAULT_AMBIENT_VOLUME
        set(value) {
            val v = value.coerceIn(0f, 1f)
            prefs?.edit { putFloat(KEY_AMBIENT_VOLUME, v) }
            val mp = activePlayer
            if (!ambientMuted && mp != null) {
                activeFadeJob?.cancel()
                activeLevel = v
                mp.setVolume(v, v)
            }
        }

    // Builds the SoundPool/MediaPlayer machinery once. Safe to call repeatedly (e.g. every
    // time HaulonautPlayScreen enters composition) -- a no-op once already initialized.
    fun init(context: Context) {
        if (soundPool != null) return
        val ctx = context.applicationContext
        appContext = ctx
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        soundPool = pool
        // Loaded off the main thread -- 21 short decodes is cheap, but no reason to block
        // screen entry on it. A play() call that lands before loading finishes just no-ops.
        scope.launch(Dispatchers.IO) {
            val ids = Sfx.entries.associateWith { pool.load(ctx, it.resId, 1) }
            soundIds.putAll(ids)
        }
    }

    // Releases the SoundPool and any ambient players. Call when leaving the Haulonaut
    // screen (ViewModel.onCleared()) -- init() rebuilds everything on the next visit.
    fun release() {
        activeFadeJob?.cancel()
        outgoingFadeJob?.cancel()
        activePlayer?.release()
        activePlayer = null
        outgoingPlayer?.release()
        outgoingPlayer = null
        currentAmbient = null
        soundPool?.release()
        soundPool = null
        soundIds.clear()
    }

    fun play(sfx: Sfx) {
        if (soundMuted) return
        val pool = soundPool ?: return
        val id = soundIds[sfx] ?: return
        val vol = soundVolume
        pool.play(id, vol, vol, 1, 0, 1f)
    }

    // Crossfades to `ambient`, or fades out to silence for a null key (e.g. during the
    // landing-sequence montage, where the foreground SFX should carry the moment). Calling
    // with the already-current key is a no-op, matching web's playHaulonautAmbient.
    fun playAmbient(ambient: Ambient?) {
        if (ambient == currentAmbient) return
        currentAmbient = ambient
        retireActivePlayer()
        if (ambient == null || ambientMuted) return
        beginActivePlayer(ambient)
    }

    fun stopAmbient() = playAmbient(null)

    private fun beginActivePlayer(ambient: Ambient) {
        val ctx = appContext ?: return
        val mp = MediaPlayer.create(ctx, ambient.resId) ?: return
        mp.isLooping = true
        mp.setVolume(0f, 0f)
        mp.start()
        activePlayer = mp
        activeLevel = 0f
        activeFadeJob?.cancel()
        activeFadeJob = scope.launch {
            fadeVolume(from = 0f, to = ambientVolume) { level ->
                activeLevel = level
                mp.setVolume(level, level)
            }
        }
    }

    private fun retireActivePlayer() {
        val mp = activePlayer ?: return
        val startLevel = activeLevel
        activePlayer = null
        activeFadeJob?.cancel()
        outgoingFadeJob?.cancel()
        outgoingPlayer?.release()
        outgoingPlayer = mp
        outgoingFadeJob = scope.launch {
            fadeVolume(from = startLevel, to = 0f) { level -> mp.setVolume(level, level) }
            mp.pause()
            mp.release()
            if (outgoingPlayer === mp) outgoingPlayer = null
        }
    }

    private suspend fun fadeVolume(from: Float, to: Float, onStep: (Float) -> Unit) {
        val steps = (AMBIENT_FADE_MS / FADE_STEP_MS).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            onStep(from + (to - from) * t)
            delay(FADE_STEP_MS)
        }
    }
}
