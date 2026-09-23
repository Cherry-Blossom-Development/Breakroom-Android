package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.audio.HaulonautEngineRoar
import com.cherryblossomdev.breakroom.audio.HaulonautSoundService
import com.cherryblossomdev.breakroom.audio.HaulonautSoundService.Ambient
import com.cherryblossomdev.breakroom.audio.HaulonautSoundService.Sfx
import com.cherryblossomdev.breakroom.data.HaulonautRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.SocketConnectionState
import com.cherryblossomdev.breakroom.data.models.SocketEvent
import com.cherryblossomdev.breakroom.network.SocketManager
import com.cherryblossomdev.breakroom.data.models.HaulonautCharacter
import com.cherryblossomdev.breakroom.data.models.HaulonautConnectedSector
import com.cherryblossomdev.breakroom.data.models.HaulonautInventoryItem
import com.cherryblossomdev.breakroom.data.models.HaulonautItem
import com.cherryblossomdev.breakroom.data.models.HaulonautKnownLocation
import com.cherryblossomdev.breakroom.data.models.HaulonautPilotState
import com.cherryblossomdev.breakroom.data.models.HaulonautSurfaceMap
import com.cherryblossomdev.breakroom.data.models.HaulonautPlayerHere
import com.cherryblossomdev.breakroom.data.models.HaulonautProbeMission
import com.cherryblossomdev.breakroom.data.models.HaulonautTrackingBuoy
import com.cherryblossomdev.breakroom.data.models.HaulonautRouteWaypoint
import com.cherryblossomdev.breakroom.data.models.HaulonautSector
import com.cherryblossomdev.breakroom.data.models.HaulonautSectorFeature
import com.cherryblossomdev.breakroom.ui.theme.isReduceMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==================== ViewModel ====================

// SPACE: the sector view. OUTPOST/CARGO/CHARTS: overlays. PLANET: the Planet Overview
// menu (Trade / Land). DOCKING: the brief descent transition. DOCKED: landed at a planet,
// still aboard the ship (Exit Craft / Launch). SURFACE: out of the craft, driving the
// buggy across the planet's surface -- replaces the whole ship UI, like web's onSurface.
enum class HaulonautViewportMode { SPACE, OUTPOST, CARGO, CHARTS, BUOYS, COMMS, PLANET, DOCKING, DOCKED, SURFACE }

private const val DRIFT_THRESHOLD = 30

// One Terminal/comms line plus when it landed -- shown as a muted HH:MM:SS beside the
// line (web parity: "per-line timestamps in the Terminal log"). Time only, no date; the
// log isn't persisted across sessions, so the day is always obvious from context.
data class HaulonautCommsLine(val text: String, val atMs: Long = System.currentTimeMillis())

// Crew health (backend migration 067) and cycles (migrations 066 + 068) -- kept in sync
// with the same-named constants in backend/routes/games.js. After the 5x rebalance
// (migration 068) a pilot holds at most 120 cycles and regains one every 12 real minutes
// (5/hour, full bar in 24h) whether or not the app is open. Actions no longer cost the
// same: a warp or a landing is 5 cycles (the big maneuvers), a buggy nudge is 1.
private const val MAX_HEALTH = 100
const val HAULONAUT_MAX_CYCLES = 120
private const val CYCLE_REPLENISH_SECONDS = 720L
private const val WARP_CYCLE_COST = 5
private const val DOCK_CYCLE_COST = 5

data class HaulonautPlayUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val character: HaulonautCharacter? = null,
    val currentSector: HaulonautSector? = null,
    val connectedSectors: List<HaulonautConnectedSector> = emptyList(),
    val features: List<HaulonautSectorFeature> = emptyList(),
    val playersHere: List<HaulonautPlayerHere> = emptyList(),
    val credits: Int = 0,
    val rations: Int = 0,
    val fuel: Int = 0,
    // Crew health, 0-100. Rations no longer block a warp -- fuel and cycles do -- but
    // warping with an empty larder starves the crew (health -15); warping with rations
    // in stock heals a little (+3). Health hitting 0 kills the character.
    val health: Int = MAX_HEALTH,
    // Server's last-synced cycle balance + epoch-seconds accrual anchor. displayedCycles
    // / nextCycleSeconds below re-run the server's replenishment math locally against
    // `nowMs` so the HUD keeps counting up between the syncs every real action performs.
    val cycles: Int = 0,
    val cyclesUpdatedAt: Long = 0,
    val nowMs: Long = System.currentTimeMillis(),
    // Flips true when the crew has died (health hit 0 on a starved warp, or the character
    // was already 'dead' on load) -- swaps in the "PILOT LOST" screen and blocks actions.
    val dead: Boolean = false,
    // Which planet feature the ship is landed at, or null in open space. Persisted
    // server-side; restored on load. Warping clears it (warping is how you undock).
    val dockedFeatureId: Int? = null,
    // Planet-surface exploration state (viewportMode SURFACE). surfaceMap is the fog-of-war
    // grid + ship/buggy positions; surfaceLog collects landing-event narrations shown on
    // the surface screen (oldest-first, newest at the bottom).
    val surfaceMap: HaulonautSurfaceMap? = null,
    val surfaceLog: List<String> = emptyList(),
    val isBuggyMoving: Boolean = false,
    val inventory: List<HaulonautInventoryItem> = emptyList(),
    val itemsCatalog: List<HaulonautItem> = emptyList(),
    val viewportMode: HaulonautViewportMode = HaulonautViewportMode.SPACE,
    val isNavigating: Boolean = false,
    val isPurchasing: Boolean = false,
    val isDocking: Boolean = false,
    // Star Charts / autopilot
    val knownLocations: List<HaulonautKnownLocation> = emptyList(),
    val isLoadingCharts: Boolean = false,
    val isTraveling: Boolean = false,
    val travelDestinationName: String? = null,
    val travelHopsRemaining: Int = 0,
    // Drift: uncontrolled movement toward the nearest planet while out of fuel
    val driftVariance: Int = 0,
    val isDrifting: Boolean = false,
    // Sector comms (viewportMode COMMS) -- the running log of the sector radio channel and
    // typed slash commands (/give, /offer, /accept, /decline, /attack). Not persisted
    // anywhere; a live channel, oldest-first, newest at the bottom. Mirrors web's TERMINAL.
    val commsLog: List<HaulonautCommsLine> = emptyList(),
    val commsInput: String = "",
    // Recon probes -- the currently deployed mission (if any). A completed/failed report
    // isn't kept in state: it's folded straight into commsLog + snackbarMessage (and
    // acknowledged) the same way trade offers are, rather than a separate banner.
    val activeProbe: HaulonautProbeMission? = null,
    val isDeployingProbe: Boolean = false,
    // Magnetic Tracking Buoys -- dropped from Cargo (no submenu, unlike probes: clicking
    // one in Cargo drops it immediately), viewed read-only in the Buoys overlay (like
    // Star Charts). Attachment is reported live via HaulonautBuoyAttached and folded
    // into commsLog + snackbarMessage the same way a probe report is.
    val buoys: List<HaulonautTrackingBuoy> = emptyList(),
    val isLoadingBuoys: Boolean = false,
    val isDroppingBuoy: Boolean = false,
    // One-shot signal, mirrors GamesUiState.createdCharacterId -- consumed by the screen
    // to show a Snackbar then cleared, so it doesn't refire on recomposition.
    val snackbarMessage: String? = null
) {
    val planetFeature: HaulonautSectorFeature? get() = features.firstOrNull { it.feature_type == "planet" }
    val outpostFeature: HaulonautSectorFeature? get() = features.firstOrNull { it.feature_type == "trading_outpost" }
    // Out of fuel and not already sitting somewhere that resolves the crisis -- mirrors
    // web's driftEligible computed exactly.
    val driftEligible: Boolean get() = fuel <= 0 && planetFeature == null
    fun inventoryQuantity(itemKey: String): Int = inventory.firstOrNull { it.item_key == itemKey }?.quantity ?: 0

    // Client-side mirror of the backend's replenishCycles(): last synced balance plus
    // whatever whole cycles have accrued since `cyclesUpdatedAt`, capped at the max.
    val displayedCycles: Int
        get() {
            if (cycles >= HAULONAUT_MAX_CYCLES) return HAULONAUT_MAX_CYCLES
            val elapsed = nowMs / 1000 - cyclesUpdatedAt
            val earned = (elapsed / CYCLE_REPLENISH_SECONDS).coerceAtLeast(0)
            return (cycles + earned).coerceAtMost(HAULONAUT_MAX_CYCLES.toLong()).toInt()
        }

    // Whole seconds until the next cycle lands, or null at the cap. The anchor only ever
    // advances in whole intervals server-side, so (elapsed % interval) is genuine progress
    // into the current interval.
    val nextCycleSeconds: Long?
        get() {
            if (displayedCycles >= HAULONAUT_MAX_CYCLES) return null
            val elapsed = nowMs / 1000 - cyclesUpdatedAt
            val intoInterval = ((elapsed % CYCLE_REPLENISH_SECONDS) + CYCLE_REPLENISH_SECONDS) % CYCLE_REPLENISH_SECONDS
            return (CYCLE_REPLENISH_SECONDS - intoInterval).coerceAtLeast(0)
        }

    // Three tiers of "not enough cycles", since actions no longer cost the same (web
    // parity -- see HaulonautPlayPage.vue): outOfCycles (< 1) gates the 1-cost buggy and
    // drives the surface HUD's red state; canAffordWarp / canAffordLanding gate the
    // 5-cost maneuvers. The server re-enforces every one of these.
    val outOfCycles: Boolean get() = displayedCycles < 1
    val canAffordWarp: Boolean get() = displayedCycles >= WARP_CYCLE_COST
    val canAffordLanding: Boolean get() = displayedCycles >= DOCK_CYCLE_COST

    val cycleCountdownLabel: String
        get() {
            val s = nextCycleSeconds ?: return ""
            return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
        }

    // Whole seconds until the pilot will have `target` cycles: time to the next one, then
    // a full interval for each after that. Drives the "warp in m:ss" hint when the bar is
    // positive but still below a maneuver's cost.
    private fun secondsUntilCycles(target: Int): Long {
        if (displayedCycles >= target) return 0
        val more = target - displayedCycles
        return (nextCycleSeconds ?: 0L) + (more - 1) * CYCLE_REPLENISH_SECONDS
    }

    private fun formatCycleWait(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    val warpReadyLabel: String get() = formatCycleWait(secondsUntilCycles(WARP_CYCLE_COST))
    val landingReadyLabel: String get() = formatCycleWait(secondsUntilCycles(DOCK_CYCLE_COST))

    val healthLow: Boolean get() = health <= 50
    val healthCritical: Boolean get() = health <= 25
    // Rations no longer stop a warp, but an empty larder means the next warp costs health.
    val rationsEmpty: Boolean get() = rations <= 0

    // The buggy has to be parked back on the ship's own cell before boarding is offered --
    // there's no unconditional "Dock with Ship" button.
    val buggyAtShip: Boolean
        get() = surfaceMap?.let { it.buggyX == it.shipX && it.buggyY == it.shipY } ?: false
    val revealedSet: Set<Int> get() = surfaceMap?.revealed?.toSet() ?: emptySet()
}

class HaulonautPlayViewModel(
    private val repository: HaulonautRepository,
    private val characterId: Int,
    private val socketManager: SocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HaulonautPlayUiState())
    val uiState: StateFlow<HaulonautPlayUiState> = _uiState.asStateFlow()

    init {
        load()
        // Live sector comms / trade / combat all ride the shared Socket.IO connection
        // (ChatService keeps it up; nudge it only if it's fully down). Events are folded
        // into commsLog / pilot state below.
        if (socketManager.connectionState.value == SocketConnectionState.DISCONNECTED) {
            socketManager.connect()
        }
        viewModelScope.launch {
            socketManager.events.collect { handleSocketEvent(it) }
        }
        // Sound cues that fire once on a state *crossing*, not on every tick it holds --
        // mirrors web's separate watch(dead) / watch(healthCritical) / watch(fuel) hooks
        // as three StateFlow collectors instead, since Kotlin has no per-field refs to
        // watch individually. `drop(1)` skips the collector's replay of the pre-load
        // default state so a fresh screen doesn't fire off the initial 0/false values.
        viewModelScope.launch {
            uiState.map { it.dead }.distinctUntilChanged().drop(1).collect { isDead ->
                if (isDead) HaulonautSoundService.play(Sfx.DEATH)
            }
        }
        viewModelScope.launch {
            uiState.map { it.healthCritical }.distinctUntilChanged().drop(1).collect { critical ->
                if (critical) HaulonautSoundService.play(Sfx.DANGER)
            }
        }
        viewModelScope.launch {
            uiState.map { it.fuel <= 0 }.distinctUntilChanged().drop(1).collect { depleted ->
                HaulonautSoundService.play(if (depleted) Sfx.ERROR else Sfx.SUCCESS)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.leaveHaulonautSector()
        HaulonautSoundService.release()
        HaulonautEngineRoar.stop(0)
    }

    // Folds the pilot-state fields every action endpoint re-sends (credits/rations/fuel/
    // health/cycles/cyclesUpdatedAt) into a state copy -- the Android equivalent of web's
    // applyPilotState(). Callers layer their own sector/snackbar changes on top with a
    // further .copy(). Death is handled separately by callers off the `died` flag.
    private fun HaulonautPlayUiState.withPilotState(s: HaulonautPilotState) = copy(
        credits = s.credits,
        rations = s.rations,
        fuel = s.fuel,
        health = s.health,
        cycles = s.cycles,
        cyclesUpdatedAt = s.cyclesUpdatedAt
    )

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.getCharacter(characterId)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        character = data.character,
                        currentSector = data.currentSector,
                        connectedSectors = data.connectedSectors,
                        features = data.features,
                        playersHere = data.playersHere,
                        credits = data.credits,
                        rations = data.rations,
                        fuel = data.fuel,
                        health = data.health,
                        cycles = data.cycles,
                        cyclesUpdatedAt = data.cyclesUpdatedAt,
                        nowMs = System.currentTimeMillis(),
                        // A character that died in an earlier session loads straight into
                        // the lost screen -- the server still 409s every action, this just
                        // skips showing a live-looking ship UI that can't do anything.
                        dead = data.character.status == "dead",
                        inventory = data.inventory,
                        dockedFeatureId = data.dockedFeatureId,
                        surfaceMap = data.surfaceMap,
                        surfaceLog = emptyList(),
                        // Restore "landed at a planet" across reloads. On the surface ->
                        // the surface screen + its map; landed-but-aboard -> the docked
                        // screen (no descent replay -- it's a stable resting state).
                        viewportMode = when {
                            data.onSurface -> HaulonautViewportMode.SURFACE
                            data.dockedFeatureId != null -> HaulonautViewportMode.DOCKED
                            else -> HaulonautViewportMode.SPACE
                        }
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to load character.")
                    return@launch
                }
            }
            // Non-fatal if this fails -- the outpost view just shows nothing for sale.
            when (val itemsResult = repository.getItems()) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(itemsCatalog = itemsResult.data)
                else -> {}
            }

            // Join this sector's live comms channel and seed the terminal, then catch up
            // on any trade offers that landed while disconnected (the live socket
            // notification only reaches an open session; offers don't expire server-side).
            if (!_uiState.value.dead) {
                socketManager.joinHaulonautSector(characterId)
                _uiState.value = _uiState.value.copy(
                    commsLog = listOf(
                        "Local comms channel open — type below to broadcast to this sector.",
                        "Trading: /give <pilot> <credits>, /offer <pilot> <item_key> <qty> for <credits>, /accept <id>, /decline <id>.",
                        "Combat: /attack <pilot> — requires a Laser Cannon in your cargo."
                    ).map { HaulonautCommsLine(it) }
                )
                loadPendingTradeOffers()
                loadProbeStatus()
            }
        }
    }

    // Incoming pending offers this character hasn't seen -- appended to the comms log so
    // they can be answered with /accept or /decline. Mirrors web's loadPendingTradeOffers.
    private fun loadPendingTradeOffers() {
        viewModelScope.launch {
            when (val result = repository.getTradeOffers(characterId)) {
                is BreakroomResult.Success -> {
                    val incoming = result.data.filter { it.to_game_user_id == characterId }
                    if (incoming.isNotEmpty()) {
                        val lines = incoming.map {
                            "[TRADE OFFER #${it.id}] ${it.from_display_name} offers ${it.quantity} ${it.item_name} " +
                                "for ${it.credits} Tokens. Type /accept ${it.id} or /decline ${it.id}."
                        }
                        appendComms(*lines.toTypedArray())
                    }
                }
                else -> {}
            }
        }
    }

    // Catches up on probe state that changed while disconnected: restores an active
    // mission's progress bar, and surfaces + acknowledges a report that resolved offline
    // (the live case is handled by handleSocketEvent's HaulonautProbeReport instead).
    private fun loadProbeStatus() {
        viewModelScope.launch {
            when (val result = repository.getProbes(characterId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(activeProbe = result.data.active)
                    result.data.report?.let { report ->
                        val line = probeReportLine(report.mission_type, report.status, report.result_summary)
                        appendComms(line)
                        _uiState.value = _uiState.value.copy(snackbarMessage = line)
                        repository.acknowledgeProbeReport(characterId, report.id)
                    }
                }
                else -> {}
            }
        }
    }

    private fun probeReportLine(missionType: String, status: String, summary: String?): String {
        val label = when (missionType) {
            "explore" -> "Explore"
            "search" -> "Search"
            "traders" -> "Trader scan"
            else -> missionType
        }
        val body = summary ?: if (status == "failed") "The probe was lost." else "Mission complete."
        return "[PROBE REPORT — $label] $body"
    }

    // Deploys the one probe in cargo on a mission. missionType is 'explore', 'search', or
    // 'traders'; searchItemKey is required (and ignored otherwise) for 'search'.
    fun deployProbe(missionType: String, searchItemKey: String? = null) {
        val state = _uiState.value
        if (state.isDeployingProbe || state.dead || state.activeProbe != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeployingProbe = true)
            when (val result = repository.deployProbe(characterId, missionType, searchItemKey)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    HaulonautSoundService.play(Sfx.SUCCESS)
                    _uiState.value = _uiState.value.copy(
                        isDeployingProbe = false,
                        inventory = data.inventory,
                        activeProbe = data.probe,
                        snackbarMessage = data.message ?: "Probe deployed."
                    )
                }
                is BreakroomResult.Error -> {
                    HaulonautSoundService.play(Sfx.ERROR)
                    _uiState.value = _uiState.value.copy(isDeployingProbe = false, snackbarMessage = result.message)
                }
                else -> _uiState.value = _uiState.value.copy(isDeployingProbe = false, snackbarMessage = "Failed to deploy probe")
            }
        }
    }

    // Lightweight cycle re-sync (no full character reload) -- called when the screen
    // resumes so a session backgrounded for hours picks up the wall-clock replenishment
    // the server accrued the whole time. Non-fatal: the local countdown keeps running off
    // the last known anchor if this fails.
    fun refreshCycles() {
        viewModelScope.launch {
            when (val result = repository.getCycles(characterId)) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    cycles = result.data.cycles,
                    cyclesUpdatedAt = result.data.cyclesUpdatedAt,
                    nowMs = System.currentTimeMillis()
                )
                else -> {}
            }
        }
    }

    fun visitOutpost() {
        HaulonautSoundService.play(Sfx.OPEN)
        val outpostName = _uiState.value.outpostFeature?.name ?: "the outpost"
        _uiState.value = _uiState.value.copy(
            viewportMode = HaulonautViewportMode.OUTPOST,
            snackbarMessage = "Docking at $outpostName."
        )
    }

    fun viewCargo() {
        HaulonautSoundService.play(Sfx.OPEN)
        _uiState.value = _uiState.value.copy(
            viewportMode = HaulonautViewportMode.CARGO,
            snackbarMessage = "Pulling up the cargo manifest."
        )
    }

    // Opens the Planet Overview menu (Trade / Land) -- see PlanetOverviewContent.
    fun planetOverview() {
        HaulonautSoundService.play(Sfx.OPEN)
        val name = _uiState.value.planetFeature?.name ?: "the planet"
        _uiState.value = _uiState.value.copy(
            viewportMode = HaulonautViewportMode.PLANET,
            snackbarMessage = "Approaching $name."
        )
    }

    // Trade at a planet reuses the outpost view/flow entirely -- same catalog, same
    // /purchase route (which now accepts a planet feature as well as a trading_outpost).
    fun enterTrade() {
        HaulonautSoundService.play(Sfx.OPEN)
        val name = _uiState.value.planetFeature?.name ?: "the planet"
        _uiState.value = _uiState.value.copy(
            viewportMode = HaulonautViewportMode.OUTPOST,
            snackbarMessage = "Opening a trade channel with $name."
        )
    }

    // The "simple transition" landing: a brief descent state, then POST /dock persists
    // "landed here" and the docked screen opens. Landing spends a cycle server-side (only
    // if it changes the docked planet) -- refuse to even start without one in hand.
    // `animate` is false under Reduce Motion: skip straight to the dock call.
    fun beginLanding(animate: Boolean) {
        val state = _uiState.value
        if (state.dead || state.isDocking) return
        if (!state.canAffordLanding) {
            _uiState.value = state.copy(
                snackbarMessage = "Cannot begin descent: landing needs $DOCK_CYCLE_COST cycles, you have ${state.displayedCycles}. Ready in ${state.landingReadyLabel}."
            )
            return
        }
        val planetName = state.planetFeature?.name ?: "the surface"
        _uiState.value = state.copy(
            viewportMode = HaulonautViewportMode.DOCKING,
            isDocking = true,
            snackbarMessage = "Beginning descent toward $planetName."
        )
        if (animate) {
            // Continuous synthesized engine roar for the whole descent instead of the two
            // disconnected one-shot bursts (Sfx.DESCENT at the start, Sfx.ENTRY ~800ms
            // later) this used to play -- swells through the approach, spikes hard at
            // atmospheric entry, then fades to silence exactly at touchdown. Mirrors
            // web's beginLandingSequence/advanceLandingPhase over Android's shorter,
            // two-beat timeline (no separate visual phase for atmospheric entry here).
            HaulonautEngineRoar.start()
            HaulonautEngineRoar.rampIntensity(0.4f, 800)
        } else {
            // Reduce Motion skips straight to the dock call -- no montage to carry a
            // continuous bed through, so Sfx.DOCK below is the only cue.
        }
        viewModelScope.launch {
            if (animate) {
                delay(800)
                HaulonautEngineRoar.spike()
                delay(800)
            }
            when (val result = repository.dock(characterId)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    if (animate) HaulonautEngineRoar.stop(200)
                    HaulonautSoundService.play(Sfx.DOCK)
                    _uiState.value = _uiState.value.copy(
                        isDocking = false,
                        dockedFeatureId = data.dockedFeatureId,
                        cycles = data.cycles,
                        cyclesUpdatedAt = data.cyclesUpdatedAt,
                        nowMs = System.currentTimeMillis(),
                        viewportMode = HaulonautViewportMode.DOCKED,
                        snackbarMessage = "Touchdown confirmed. Docking clamps engaged."
                    )
                }
                is BreakroomResult.Error -> {
                    if (animate) HaulonautEngineRoar.stop()
                    _uiState.value = _uiState.value.copy(
                        isDocking = false,
                        viewportMode = HaulonautViewportMode.PLANET,
                        snackbarMessage = result.message
                    )
                }
                else -> {
                    if (animate) HaulonautEngineRoar.stop()
                    _uiState.value = _uiState.value.copy(
                        isDocking = false,
                        viewportMode = HaulonautViewportMode.PLANET,
                        snackbarMessage = "Docking failed"
                    )
                }
            }
        }
    }

    // The undock counterpart -- clears the docked state and returns to open space without
    // warping anywhere. Best-effort like web: a failed call just means the next reload
    // would restore the docked screen, self-correcting by launching again.
    fun launch() {
        if (_uiState.value.dead) return
        // Spike-then-fade engine roar (mirrors web's launch roar: hot right on ignition,
        // easing to silence through the climb-away) instead of the single one-shot
        // LAUNCH clip. Android's launch transition is instant (no staged ignition/
        // departing UI to hold it against, unlike web's ~4.6s montage), so the roar
        // plays out in the background over the climb rather than gating the viewport flip.
        HaulonautEngineRoar.start()
        HaulonautEngineRoar.spike()
        viewModelScope.launch {
            delay(600)
            HaulonautEngineRoar.stop(600)
        }
        _uiState.value = _uiState.value.copy(
            viewportMode = HaulonautViewportMode.SPACE,
            dockedFeatureId = null,
            snackbarMessage = "Breaking orbit. Back in open space."
        )
        viewModelScope.launch { repository.launch(characterId) }
    }

    // Steps out of the docked ship onto the planet surface. Optimistic: flips to the
    // surface view immediately and loads the persisted map state; a failure drops back to
    // the docked screen.
    fun exitCraft() {
        if (_uiState.value.dead || _uiState.value.viewportMode != HaulonautViewportMode.DOCKED) return
        HaulonautSoundService.play(Sfx.OPEN)
        _uiState.value = _uiState.value.copy(
            viewportMode = HaulonautViewportMode.SURFACE,
            surfaceLog = emptyList(),
            snackbarMessage = "Exiting craft."
        )
        viewModelScope.launch {
            when (val result = repository.exitCraft(characterId)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    _uiState.value = _uiState.value.copy(
                        surfaceMap = data.surfaceMap,
                        cycles = data.cycles,
                        cyclesUpdatedAt = data.cyclesUpdatedAt,
                        nowMs = System.currentTimeMillis()
                    )
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    viewportMode = HaulonautViewportMode.DOCKED,
                    snackbarMessage = result.message
                )
                else -> _uiState.value = _uiState.value.copy(
                    viewportMode = HaulonautViewportMode.DOCKED,
                    snackbarMessage = "Failed to exit craft"
                )
            }
        }
    }

    // One cell of buggy movement. Every move to a new cell costs a cycle (server-enforced)
    // -- blocked at 0, which can strand the buggy away from the ship until one replenishes.
    fun driveBuggy(direction: String) {
        val state = _uiState.value
        if (state.isBuggyMoving || state.surfaceMap == null || state.dead) return
        if (state.outOfCycles) {
            HaulonautSoundService.play(Sfx.ERROR)
            _uiState.value = state.copy(
                snackbarMessage = "Out of cycles — the buggy is parked. +1 in ${state.cycleCountdownLabel}"
            )
            return
        }
        _uiState.value = state.copy(isBuggyMoving = true)
        viewModelScope.launch {
            when (val result = repository.driveBuggy(characterId, direction)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    val map = _uiState.value.surfaceMap
                    val newMap = map?.copy(buggyX = data.buggyX, buggyY = data.buggyY, revealed = data.revealed)
                    // Landing event (first visit to this cell) gets its own distinct cue;
                    // a plain move to an already-seen cell that actually moved gets the
                    // lighter one; bumping the grid's edge (no position change) is silent,
                    // matching web's actuallyMoved gate.
                    val actuallyMoved = map != null && (data.buggyX != map.buggyX || data.buggyY != map.buggyY)
                    if (data.narration != null) {
                        HaulonautSoundService.play(Sfx.LANDING_EVENT)
                    } else if (actuallyMoved) {
                        HaulonautSoundService.play(Sfx.BUGGY_MOVE)
                    }
                    // Landing event (first visit to this cell): narration + optional
                    // credits/rations/fuel deltas that arrive as new totals.
                    val logLine = data.narration
                    val deltaParts = buildList {
                        data.effects?.credits?.takeIf { it != 0 }?.let { add("${if (it > 0) "+" else ""}$it Tokens") }
                        data.effects?.rations?.takeIf { it != 0 }?.let { add("${if (it > 0) "+" else ""}$it Rations") }
                        data.effects?.fuel?.takeIf { it != 0 }?.let { add("${if (it > 0) "+" else ""}$it Fuel") }
                    }
                    _uiState.value = _uiState.value.copy(
                        isBuggyMoving = false,
                        surfaceMap = newMap,
                        cycles = data.cycles,
                        cyclesUpdatedAt = data.cyclesUpdatedAt,
                        nowMs = System.currentTimeMillis(),
                        credits = data.credits ?: _uiState.value.credits,
                        rations = data.rations ?: _uiState.value.rations,
                        fuel = data.fuel ?: _uiState.value.fuel,
                        surfaceLog = if (logLine != null) {
                            _uiState.value.surfaceLog + logLine + if (deltaParts.isNotEmpty()) listOf("(${deltaParts.joinToString(", ")})") else emptyList()
                        } else {
                            _uiState.value.surfaceLog
                        }
                    )
                }
                is BreakroomResult.Error -> {
                    HaulonautSoundService.play(Sfx.ERROR)
                    _uiState.value = _uiState.value.copy(
                        isBuggyMoving = false,
                        snackbarMessage = result.message
                    )
                }
                else -> _uiState.value = _uiState.value.copy(isBuggyMoving = false)
            }
        }
    }

    // Boards the ship from the surface -- only offered (and only allowed server-side) once
    // the buggy is parked on the ship's cell. Docking status is unchanged: this returns to
    // the docked screen, not open space.
    fun returnToShip() {
        val state = _uiState.value
        if (!state.buggyAtShip) return
        HaulonautSoundService.play(Sfx.CLICK)
        _uiState.value = state.copy(
            viewportMode = HaulonautViewportMode.DOCKED,
            surfaceMap = null,
            snackbarMessage = "Boarding the ship. Systems coming back online."
        )
        viewModelScope.launch { repository.returnToShip(characterId) }
    }

    fun exitViewportOverlay() {
        HaulonautSoundService.play(Sfx.CLICK)
        val message = when (_uiState.value.viewportMode) {
            HaulonautViewportMode.OUTPOST -> "Departing the outpost."
            HaulonautViewportMode.CHARTS -> "Closing star charts."
            HaulonautViewportMode.BUOYS -> "Closing buoy telemetry."
            HaulonautViewportMode.PLANET -> "Breaking orbit."
            HaulonautViewportMode.COMMS -> "Closing the comms channel."
            else -> "Closing the cargo manifest."
        }
        _uiState.value = _uiState.value.copy(viewportMode = HaulonautViewportMode.SPACE, snackbarMessage = message)
    }

    fun purchase(item: HaulonautItem) {
        if (_uiState.value.isPurchasing || _uiState.value.dead) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPurchasing = true)
            when (val result = repository.purchase(characterId, item.item_key, 1)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    HaulonautSoundService.play(Sfx.SUCCESS)
                    _uiState.value = _uiState.value.withPilotState(data).copy(
                        isPurchasing = false,
                        inventory = data.inventory,
                        snackbarMessage = "Purchased 1 ${item.name}. (-${item.base_price} Tokens)"
                    )
                }
                is BreakroomResult.Error -> {
                    HaulonautSoundService.play(Sfx.ERROR)
                    _uiState.value = _uiState.value.copy(
                        isPurchasing = false,
                        snackbarMessage = result.message
                    )
                }
                else -> {
                    HaulonautSoundService.play(Sfx.ERROR)
                    _uiState.value = _uiState.value.copy(isPurchasing = false, snackbarMessage = "Purchase failed")
                }
            }
        }
    }

    // Manual warp (click or keyboard on the nav box) always takes precedence over an
    // in-progress autopilot course -- silently cancels it before the manual warp goes
    // through (the manual warp's own "Arrived in Sector X" message follows immediately,
    // so no separate "disengaged" message is needed here, matching web's manualNavigateTo()).
    fun navigate(sector: HaulonautConnectedSector) {
        if (_uiState.value.isNavigating || _uiState.value.dead) return
        if (_uiState.value.isTraveling) {
            _uiState.value = _uiState.value.copy(isTraveling = false, travelDestinationName = null, travelHopsRemaining = 0)
        }
        viewModelScope.launch { runNavigate(sector.id, sector.sector_number, manual = true) }
    }

    // Shared warp path for a manual warp and each autopilot hop. Returns true only on a
    // successful, survived warp -- travelAlongPath uses that to decide whether to continue
    // the course. A warp costs a cycle (server-enforced); blocked up front so an autopilot
    // course also stops here rather than firing a doomed request per hop.
    private suspend fun runNavigate(toSectorId: Int, toSectorNumber: Int, manual: Boolean): Boolean {
        val state = _uiState.value
        if (state.dead) return false
        if (!state.canAffordWarp) {
            HaulonautSoundService.play(Sfx.ERROR)
            _uiState.value = state.copy(
                isNavigating = false,
                isTraveling = false,
                travelDestinationName = null,
                travelHopsRemaining = 0,
                snackbarMessage = "Warp drive offline: needs $WARP_CYCLE_COST cycles, ${state.displayedCycles} available. Ready in ${state.warpReadyLabel}."
            )
            return false
        }
        _uiState.value = _uiState.value.copy(isNavigating = true)
        HaulonautSoundService.play(Sfx.WARP)
        return when (val result = repository.navigate(characterId, toSectorId)) {
            is BreakroomResult.Success -> {
                val data = result.data
                val died = data.died
                _uiState.value = _uiState.value.withPilotState(data).copy(
                    isNavigating = false,
                    currentSector = data.currentSector,
                    connectedSectors = data.connectedSectors,
                    features = data.features,
                    playersHere = data.playersHere,
                    viewportMode = HaulonautViewportMode.SPACE,
                    // Warping always undocks server-side -- mirror that so no stale
                    // "docked"/surface state survives the jump.
                    dockedFeatureId = data.dockedFeatureId,
                    surfaceMap = data.surfaceMap,
                    surfaceLog = emptyList(),
                    dead = died,
                    // Not touched on an autopilot hop -- travelAlongPath owns the
                    // "N hops remaining" message; a manual warp gets the arrival line.
                    snackbarMessage = when {
                        died -> "The crew did not survive the jump. Life support flatlined."
                        manual -> "Arrived in Sector ${data.currentSector?.sector_number ?: "?"}."
                        else -> _uiState.value.snackbarMessage
                    }
                )
                // Re-join the sector comms room wherever the warp landed. `dead` (if it
                // flipped true) gets its own cue from the ViewModel-wide watcher in init{}
                // rather than here, so arrival stays specific to a warp that was survived.
                if (!died) {
                    socketManager.joinHaulonautSector(characterId)
                    HaulonautSoundService.play(Sfx.ARRIVAL)
                }
                !died
            }
            is BreakroomResult.Error -> {
                HaulonautSoundService.play(Sfx.ERROR)
                _uiState.value = _uiState.value.copy(isNavigating = false, snackbarMessage = result.message)
                false
            }
            else -> {
                HaulonautSoundService.play(Sfx.ERROR)
                _uiState.value = _uiState.value.copy(isNavigating = false, snackbarMessage = "Navigation failed")
                false
            }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    // ==================== Star Charts / autopilot ====================

    fun viewStarCharts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCharts = true)
            when (val result = repository.getKnownLocations(characterId)) {
                is BreakroomResult.Success -> {
                    HaulonautSoundService.play(Sfx.OPEN)
                    _uiState.value = _uiState.value.copy(
                        isLoadingCharts = false,
                        knownLocations = result.data,
                        viewportMode = HaulonautViewportMode.CHARTS,
                        snackbarMessage = "Pulling up star charts."
                    )
                }
                is BreakroomResult.Error -> {
                    HaulonautSoundService.play(Sfx.ERROR)
                    _uiState.value = _uiState.value.copy(
                        isLoadingCharts = false,
                        snackbarMessage = result.message
                    )
                }
                else -> {
                    HaulonautSoundService.play(Sfx.ERROR)
                    _uiState.value = _uiState.value.copy(isLoadingCharts = false, snackbarMessage = "Failed to load star charts")
                }
            }
        }
    }

    fun setCourse(location: HaulonautKnownLocation) {
        if (_uiState.value.isTraveling || _uiState.value.dead) return
        if (!_uiState.value.canAffordWarp) {
            HaulonautSoundService.play(Sfx.ERROR)
            _uiState.value = _uiState.value.copy(
                snackbarMessage = "Warp drive offline: needs $WARP_CYCLE_COST cycles, ${_uiState.value.displayedCycles} available. Ready in ${_uiState.value.warpReadyLabel}."
            )
            return
        }
        viewModelScope.launch {
            when (val result = repository.getRoute(characterId, location.sector_id)) {
                is BreakroomResult.Success -> {
                    val path = result.data
                    if (path.size <= 1) return@launch
                    val hops = path.size - 1
                    _uiState.value = _uiState.value.copy(
                        viewportMode = HaulonautViewportMode.SPACE,
                        isTraveling = true,
                        travelDestinationName = location.name,
                        travelHopsRemaining = hops,
                        snackbarMessage = "Course plotted to ${location.name} ($hops hop${if (hops == 1) "" else "s"}). Autopilot engaged."
                    )
                    travelAlongPath(path)
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(snackbarMessage = result.message)
                else -> _uiState.value = _uiState.value.copy(snackbarMessage = "Failed to plot course")
            }
        }
    }

    // Flies the character along a precomputed path (path[0] is the current sector,
    // skipped) one hop at a time via the same repository.navigate() a manual warp uses --
    // rations still drain and each link is still re-validated server-side. isTraveling
    // doubles as the cancellation switch: navigate() (a manual warp) or abortAutopilot()
    // can flip it false from outside, and this loop rechecks it before every hop.
    private suspend fun travelAlongPath(path: List<HaulonautRouteWaypoint>) {
        for (i in 1 until path.size) {
            if (!_uiState.value.isTraveling) return
            val ok = runNavigate(path[i].id, path[i].sector_number, manual = false)
            if (!ok) {
                // runNavigate already set the stop reason (out of cycles, a rejected hop,
                // or crew death) into snackbarMessage and cleared isTraveling.
                _uiState.value = _uiState.value.copy(
                    isTraveling = false,
                    travelDestinationName = null,
                    travelHopsRemaining = 0
                )
                return
            }
            _uiState.value = _uiState.value.copy(travelHopsRemaining = path.size - 1 - i)
            if (_uiState.value.isTraveling && i < path.size - 1) delay(600)
        }
        if (_uiState.value.isTraveling) {
            _uiState.value = _uiState.value.copy(
                isTraveling = false,
                travelDestinationName = null,
                travelHopsRemaining = 0,
                snackbarMessage = "Arrived at destination."
            )
        }
    }

    fun abortAutopilot() {
        if (!_uiState.value.isTraveling) return
        _uiState.value = _uiState.value.copy(
            isTraveling = false,
            travelDestinationName = null,
            travelHopsRemaining = 0,
            snackbarMessage = "Autopilot disengaged."
        )
    }

    // ==================== Magnetic Tracking Buoys ====================

    // Read-only status screen (like Star Charts) -- refreshed whenever it's opened rather
    // than proactively, since an attached buoy's target location only usefully changes
    // while this screen is actually open to see it.
    fun viewBuoys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBuoys = true)
            when (val result = repository.getBuoys(characterId)) {
                is BreakroomResult.Success -> {
                    HaulonautSoundService.play(Sfx.OPEN)
                    _uiState.value = _uiState.value.copy(
                        isLoadingBuoys = false,
                        buoys = result.data.buoys,
                        viewportMode = HaulonautViewportMode.BUOYS,
                        snackbarMessage = "Checking tracking buoy telemetry."
                    )
                }
                is BreakroomResult.Error -> {
                    HaulonautSoundService.play(Sfx.ERROR)
                    _uiState.value = _uiState.value.copy(isLoadingBuoys = false, snackbarMessage = result.message)
                }
                else -> {
                    HaulonautSoundService.play(Sfx.ERROR)
                    _uiState.value = _uiState.value.copy(isLoadingBuoys = false, snackbarMessage = "Failed to load buoy telemetry")
                }
            }
        }
    }

    // Drops the one tracking buoy in cargo into the current sector. Unlike deployProbe,
    // there's no submenu to step through first -- clicking the item in Cargo is the whole
    // interaction -- so this closes the overlay back out to SPACE on success.
    fun dropBuoy() {
        val state = _uiState.value
        if (state.isDroppingBuoy || state.dead) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDroppingBuoy = true)
            when (val result = repository.dropBuoy(characterId)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    HaulonautSoundService.play(Sfx.SUCCESS)
                    _uiState.value = _uiState.value.copy(
                        isDroppingBuoy = false,
                        inventory = data.inventory,
                        buoys = data.buoys,
                        viewportMode = HaulonautViewportMode.SPACE,
                        snackbarMessage = data.message ?: "Tracking buoy dropped."
                    )
                }
                is BreakroomResult.Error -> {
                    HaulonautSoundService.play(Sfx.ERROR)
                    _uiState.value = _uiState.value.copy(isDroppingBuoy = false, snackbarMessage = result.message)
                }
                else -> {
                    HaulonautSoundService.play(Sfx.ERROR)
                    _uiState.value = _uiState.value.copy(isDroppingBuoy = false, snackbarMessage = "Failed to drop tracking buoy")
                }
            }
        }
    }

    // ==================== Drift ====================

    // Called once per second by the screen's lifecycle-gated ticker (only while the play
    // screen is actually resumed -- see HaulonautPlayScreen's repeatOnLifecycle block),
    // mirroring web's identical gate on document.visibilityState. Climbs driftVariance by
    // a random 1-3 while eligible; crossing DRIFT_THRESHOLD triggers one drift hop.
    fun driftTick() {
        // Advance the wall clock the cycle countdown / displayedCycles read off first,
        // every tick regardless of drift state -- this is the only 1s timer in the screen.
        val state = _uiState.value.copy(nowMs = System.currentTimeMillis())
        _uiState.value = state
        if (state.dead) return
        if (!state.driftEligible) {
            if (state.driftVariance != 0) _uiState.value = state.copy(driftVariance = 0)
            return
        }
        if (state.isDrifting || state.isTraveling) return
        val newVariance = state.driftVariance + 1 + Random.nextInt(3)
        _uiState.value = state.copy(driftVariance = newVariance)
        if (newVariance >= DRIFT_THRESHOLD) performDrift()
    }

    // One hop toward the nearest planet (server-computed) -- doesn't touch
    // credits/rations/fuel, since this is uncontrolled momentum, not a piloted warp. A
    // rejection (fuel was restored, or a planet was reached between ticks) is a quiet
    // no-op, same as web's `if (!res.ok) return` -- the next tick's driftEligible check
    // resolves it either way.
    private fun performDrift() {
        if (_uiState.value.isDrifting) return
        _uiState.value = _uiState.value.copy(isDrifting = true)
        viewModelScope.launch {
            when (val result = repository.drift(characterId)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    HaulonautSoundService.play(Sfx.DRIFT)
                    val arrivedAtPlanet = data.features.any { it.feature_type == "planet" }
                    val planetNote = if (arrivedAtPlanet) " A planetary body is in range. Drift variance stabilizing." else ""
                    _uiState.value = _uiState.value.withPilotState(data).copy(
                        isDrifting = false,
                        currentSector = data.currentSector,
                        connectedSectors = data.connectedSectors,
                        features = data.features,
                        playersHere = data.playersHere,
                        viewportMode = HaulonautViewportMode.SPACE,
                        driftVariance = 0,
                        snackbarMessage = "DRIFT: hull carried into Sector ${data.currentSector?.sector_number ?: "?"}.$planetNote"
                    )
                    socketManager.joinHaulonautSector(characterId)
                }
                else -> _uiState.value = _uiState.value.copy(isDrifting = false)
            }
        }
    }

    // ==================== Sector comms / trading / combat ====================

    fun openComms() {
        HaulonautSoundService.play(Sfx.OPEN)
        _uiState.value = _uiState.value.copy(viewportMode = HaulonautViewportMode.COMMS)
    }

    fun setCommsInput(value: String) {
        _uiState.value = _uiState.value.copy(commsInput = value)
    }

    private fun appendComms(vararg lines: String) {
        // Cap the log so a long session doesn't grow it without bound (web lets it grow;
        // 200 lines is well past what the panel shows and keeps recomposition cheap).
        val now = System.currentTimeMillis()
        val next = (_uiState.value.commsLog + lines.map { HaulonautCommsLine(it, now) }).takeLast(200)
        _uiState.value = _uiState.value.copy(commsLog = next)
    }

    private fun findPilotHere(name: String): HaulonautPlayerHere? {
        val lower = name.trim().lowercase()
        return _uiState.value.playersHere.firstOrNull { it.display_name.lowercase() == lower }
    }

    // Anything typed into the comms box. A leading '/' is a command (see handleSlashCommand);
    // everything else is broadcast to the sector over the socket. The raw line is echoed
    // into the log either way, matching web's terminal.
    fun submitCommsInput() {
        val text = _uiState.value.commsInput.trim()
        if (text.isEmpty()) return
        _uiState.value = _uiState.value.copy(commsInput = "")
        appendComms(text)
        if (text.startsWith("/")) {
            handleSlashCommand(text.substring(1))
        } else {
            socketManager.sendHaulonautSectorMessage(characterId, text)
        }
    }

    private fun handleSlashCommand(rest: String) {
        val tokens = rest.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val cmd = tokens.firstOrNull()?.lowercase() ?: ""
        val args = tokens.drop(1)

        when (cmd) {
            "give" -> {
                val amount = args.lastOrNull()?.toIntOrNull()
                val name = args.dropLast(1).joinToString(" ")
                if (name.isEmpty() || amount == null || amount <= 0) {
                    appendComms("Usage: /give <pilot name> <credits>"); return
                }
                val target = findPilotHere(name) ?: run { appendComms("No pilot named \"$name\" in this sector."); return }
                viewModelScope.launch {
                    when (val r = repository.give(characterId, target.id, amount)) {
                        is BreakroomResult.Success -> {
                            HaulonautSoundService.play(Sfx.TRADE_SUCCESS)
                            appendComms(r.data.message ?: "Tokens sent.")
                            r.data.credits?.let { _uiState.value = _uiState.value.copy(credits = it) }
                        }
                        is BreakroomResult.Error -> {
                            HaulonautSoundService.play(Sfx.ERROR)
                            appendComms(r.message)
                        }
                        else -> {
                            HaulonautSoundService.play(Sfx.ERROR)
                            appendComms("Transmission failed.")
                        }
                    }
                }
            }
            "offer" -> {
                // /offer <name...> <item_key> <qty> for <credits>
                if (args.size < 5) { appendComms("Usage: /offer <pilot name> <item_key> <qty> for <credits>"); return }
                val credits = args[args.size - 1].toIntOrNull()
                val forWord = args[args.size - 2]
                val quantity = args[args.size - 3].toIntOrNull()
                val itemKey = args[args.size - 4]
                val name = args.dropLast(4).joinToString(" ")
                if (name.isEmpty() || quantity == null || quantity <= 0 || credits == null || credits < 0 || !forWord.equals("for", ignoreCase = true)) {
                    appendComms("Usage: /offer <pilot name> <item_key> <qty> for <credits>"); return
                }
                val target = findPilotHere(name) ?: run { appendComms("No pilot named \"$name\" in this sector."); return }
                viewModelScope.launch {
                    when (val r = repository.createTradeOffer(characterId, target.id, itemKey, quantity, credits)) {
                        // Web also distinguishes an NPC's instant accept/decline here
                        // (npcResponse) -- Android's HaulonautCreateTradeOfferResponse
                        // doesn't carry that field yet, so a sent offer just plays the
                        // generic success cue; a live accept/decline (human or NPC) still
                        // gets its own distinct sound via handleSocketEvent below.
                        is BreakroomResult.Success -> {
                            HaulonautSoundService.play(Sfx.SUCCESS)
                            appendComms(r.data.message ?: "Trade offer sent.")
                        }
                        is BreakroomResult.Error -> {
                            HaulonautSoundService.play(Sfx.ERROR)
                            appendComms(r.message)
                        }
                        else -> {
                            HaulonautSoundService.play(Sfx.ERROR)
                            appendComms("Transmission failed.")
                        }
                    }
                }
            }
            "accept", "decline" -> {
                val offerId = args.lastOrNull()?.toIntOrNull()
                if (offerId == null) { appendComms("Usage: /$cmd <offer id>"); return }
                viewModelScope.launch {
                    if (cmd == "accept") {
                        when (val r = repository.acceptTradeOffer(characterId, offerId)) {
                            is BreakroomResult.Success -> {
                                HaulonautSoundService.play(Sfx.TRADE_SUCCESS)
                                appendComms(r.data.message ?: "Trade complete.")
                                r.data.credits?.let { _uiState.value = _uiState.value.copy(credits = it) }
                                _uiState.value = _uiState.value.copy(inventory = r.data.inventory)
                            }
                            is BreakroomResult.Error -> {
                                HaulonautSoundService.play(Sfx.ERROR)
                                appendComms(r.message)
                            }
                            else -> {
                                HaulonautSoundService.play(Sfx.ERROR)
                                appendComms("Transmission failed.")
                            }
                        }
                    } else {
                        when (val r = repository.declineTradeOffer(characterId, offerId)) {
                            is BreakroomResult.Success -> {
                                HaulonautSoundService.play(Sfx.TRADE_DECLINE)
                                appendComms(r.data.message ?: "Trade offer declined.")
                            }
                            is BreakroomResult.Error -> {
                                HaulonautSoundService.play(Sfx.ERROR)
                                appendComms(r.message)
                            }
                            else -> {
                                HaulonautSoundService.play(Sfx.ERROR)
                                appendComms("Transmission failed.")
                            }
                        }
                    }
                }
            }
            "attack" -> {
                val name = args.joinToString(" ")
                if (name.isEmpty()) { appendComms("Usage: /attack <pilot name>"); return }
                val target = findPilotHere(name) ?: run { appendComms("No pilot named \"$name\" in this sector."); return }
                viewModelScope.launch {
                    when (val r = repository.attack(characterId, target.id)) {
                        // A hit's outcome is broadcast to the whole sector (haulonaut_combat_event)
                        // and logged from there -- say nothing extra on success.
                        is BreakroomResult.Success -> {}
                        is BreakroomResult.Error -> {
                            HaulonautSoundService.play(Sfx.ERROR)
                            appendComms(r.message)
                        }
                        else -> {
                            HaulonautSoundService.play(Sfx.ERROR)
                            appendComms("Attack failed.")
                        }
                    }
                }
            }
            else -> {
                HaulonautSoundService.play(Sfx.ERROR)
                appendComms("Command not recognized.")
            }
        }
    }

    private fun handleSocketEvent(event: SocketEvent) {
        val sectorId = _uiState.value.currentSector?.id
        when (event) {
            is SocketEvent.HaulonautSectorMessage -> {
                if (event.sectorId == sectorId && event.characterId != characterId) {
                    appendComms("${event.displayName}: ${event.message}")
                }
            }
            is SocketEvent.HaulonautCombatEvent -> {
                if (event.sectorId != sectorId) return
                val involvesMe = event.fromCharacterId == characterId || event.toCharacterId == characterId
                val line = when {
                    event.toCharacterId == characterId ->
                        "${event.fromDisplayName} attacks you for ${event.damage} damage! Health: ${event.targetHealth}."
                    event.fromCharacterId == characterId ->
                        "You hit ${event.toDisplayName} for ${event.damage} damage. Their health: ${event.targetHealth}."
                    else ->
                        "${event.fromDisplayName} attacks ${event.toDisplayName} for ${event.damage} damage."
                }
                // A death here plays no sound of its own -- the dead-crossing watcher in
                // init{} owns Sfx.DEATH regardless of how the crew died (combat or a
                // starved warp), matching web's single watch(dead) hook.
                if (event.toCharacterId == characterId && !event.died) {
                    HaulonautSoundService.play(Sfx.DAMAGE)
                } else if (event.fromCharacterId == characterId) {
                    HaulonautSoundService.play(Sfx.HIT)
                }
                appendComms(line)
                _uiState.value = _uiState.value.copy(
                    health = if (event.toCharacterId == characterId) event.targetHealth else _uiState.value.health,
                    dead = _uiState.value.dead || (event.toCharacterId == characterId && event.died),
                    snackbarMessage = if (involvesMe) line else _uiState.value.snackbarMessage
                )
            }
            is SocketEvent.HaulonautGiftReceived -> {
                HaulonautSoundService.play(Sfx.TRADE_SUCCESS)
                appendComms("${event.fromDisplayName} gave you ${event.credits} Tokens.")
                _uiState.value = _uiState.value.copy(
                    credits = event.newBalance ?: _uiState.value.credits,
                    snackbarMessage = "${event.fromDisplayName} gave you ${event.credits} Tokens."
                )
            }
            is SocketEvent.HaulonautTradeOffer -> {
                HaulonautSoundService.play(Sfx.NOTIFY)
                appendComms(
                    "[TRADE OFFER #${event.offerId}] ${event.fromDisplayName} offers ${event.quantity} ${event.itemName} " +
                        "for ${event.credits} Tokens. Type /accept ${event.offerId} or /decline ${event.offerId}."
                )
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Trade offer #${event.offerId} from ${event.fromDisplayName}"
                )
            }
            is SocketEvent.HaulonautTradeResolved -> {
                HaulonautSoundService.play(if (event.accepted) Sfx.TRADE_SUCCESS else Sfx.TRADE_DECLINE)
                val line = if (event.accepted) {
                    "Trade #${event.offerId} accepted: you received ${event.credits} Tokens for ${event.quantity} ${event.itemName}."
                } else {
                    "Trade #${event.offerId} declined."
                }
                appendComms(line)
                _uiState.value = _uiState.value.copy(
                    credits = if (event.accepted) event.newBalance ?: _uiState.value.credits else _uiState.value.credits,
                    snackbarMessage = line
                )
            }
            is SocketEvent.HaulonautProbeReport -> {
                HaulonautSoundService.play(Sfx.NOTIFY)
                val line = probeReportLine(event.missionType, event.status, event.summary)
                appendComms(line)
                _uiState.value = _uiState.value.copy(
                    activeProbe = null,
                    snackbarMessage = line
                )
                viewModelScope.launch { repository.acknowledgeProbeReport(characterId, event.missionId) }
            }
            is SocketEvent.HaulonautBuoyAttached -> {
                HaulonautSoundService.play(Sfx.NOTIFY)
                val line = "[BUOY] Your tracking buoy just attached to ${event.targetDisplayName}'s ship."
                appendComms(line)
                _uiState.value = _uiState.value.copy(snackbarMessage = line)
                // Silently refresh the read-only telemetry list if it's already open --
                // no loading spinner / sound / snackbar churn on top of the alert above.
                if (_uiState.value.viewportMode == HaulonautViewportMode.BUOYS) {
                    viewModelScope.launch {
                        when (val result = repository.getBuoys(characterId)) {
                            is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(buoys = result.data.buoys)
                            else -> {}
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

// ==================== Screen ====================

// Deterministic-per-name hue so the same planet always renders the same color when
// revisited, without needing to store a color anywhere -- ports hashHue() from the web
// client's viewport scene.
private fun hashHue(str: String): Float {
    var hash = 0
    for (c in str) hash = (hash * 31 + c.code) % 360
    return hash.toFloat()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaulonautPlayScreen(
    viewModel: HaulonautPlayViewModel,
    onExit: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbarMessage()
        }
    }

    // Builds the SoundPool/ambient machinery once per screen visit (a no-op if already
    // built); ViewModel.onCleared() tears it down, so a fresh visit needs this again.
    val soundContext = LocalContext.current
    LaunchedEffect(Unit) { HaulonautSoundService.init(soundContext.applicationContext) }

    // Which ambient bed should be playing right now, purely a function of where the
    // character is -- mirrors web's ambientContext computed: surface ambience while
    // walking around a planet, outpost ambience while browsing its wares, silence during
    // the brief descent transition (the descent/entry/dock SFX carry that moment instead),
    // and the default space drone otherwise.
    val ambientKey = when (state.viewportMode) {
        HaulonautViewportMode.SURFACE -> Ambient.SURFACE
        HaulonautViewportMode.OUTPOST -> Ambient.OUTPOST
        HaulonautViewportMode.DOCKING -> null
        else -> Ambient.SPACE
    }
    LaunchedEffect(ambientKey) { HaulonautSoundService.playAmbient(ambientKey) }
    DisposableEffect(Unit) { onDispose { HaulonautSoundService.stopAmbient() } }

    // Drift ticks once per second, but only while this screen is actually resumed --
    // repeatOnLifecycle cancels the block (and any pending delay) the moment it isn't,
    // and restarts it fresh on return. This is the Android equivalent of web's
    // `document.visibilityState === 'visible'` gate: backgrounding the app or navigating
    // away freezes drift in place instead of racking it up to unleash on return.
    val reduceMotion = isReduceMotionEnabled()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Re-sync cycles on every resume -- a session backgrounded for hours has been
            // accruing wall-clock replenishment server-side the whole time (web does the
            // equivalent on visibilitychange).
            viewModel.refreshCycles()
            while (true) {
                delay(1000)
                viewModel.driftTick()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.character?.display_name ?: "Haulonaut") },
                navigationIcon = {
                    IconButton(onClick = onExit, modifier = Modifier.semantics { contentDescription = "Exit to Games" }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // The surface screen carries its own stat row -- keep the app bar
                    // clear there, like web's chrome-free surface view.
                    if (state.character != null && state.viewportMode != HaulonautViewportMode.SURFACE) {
                        ResourcePill(label = "Tokens", value = state.credits, tagKey = "credits")
                        Spacer(modifier = Modifier.width(8.dp))
                        ResourcePill(label = "Rations", value = state.rations)
                        Spacer(modifier = Modifier.width(8.dp))
                        ResourcePill(label = "Fuel", value = state.fuel)
                        if (state.driftEligible) {
                            Spacer(modifier = Modifier.width(8.dp))
                            DriftVariancePill(value = state.driftVariance)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    HaulonautSoundControls()
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).testTag("screen-haulonaut-play")) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.load() }) { Text("Retry") }
                }
                // The crew starved to death on a warp with no rations (health hit 0), or
                // this character was already dead on load. Every server action 409s for a
                // dead pilot; this replaces the whole live UI with an end state.
                state.dead -> PilotLostContent(
                    name = state.character?.display_name ?: "",
                    onExit = onExit,
                    modifier = Modifier.align(Alignment.Center)
                )
                // Out of the craft: the surface screen replaces the whole ship UI (no
                // status HUD strip, no bottom bar), like web's onSurface branch.
                state.viewportMode == HaulonautViewportMode.SURFACE -> SurfaceContent(
                    state = state,
                    onDrive = { viewModel.driveBuggy(it) },
                    onReturnToShip = { viewModel.returnToShip() }
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    HaulonautStatusHud(state)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (state.viewportMode) {
                            HaulonautViewportMode.SPACE -> SpaceSceneContent(state)
                            HaulonautViewportMode.OUTPOST -> OutpostContent(
                                state = state,
                                onPurchase = { viewModel.purchase(it) }
                            )
                            HaulonautViewportMode.CARGO -> CargoContent(
                                state = state,
                                onDeployProbe = { type, searchKey -> viewModel.deployProbe(type, searchKey) },
                                onDropBuoy = { viewModel.dropBuoy() }
                            )
                            HaulonautViewportMode.CHARTS -> ChartsContent(
                                state = state,
                                onSetCourse = { viewModel.setCourse(it) }
                            )
                            HaulonautViewportMode.BUOYS -> BuoysContent(state = state)
                            HaulonautViewportMode.COMMS -> CommsContent(
                                state = state,
                                onInputChange = { viewModel.setCommsInput(it) },
                                onSubmit = { viewModel.submitCommsInput() }
                            )
                            HaulonautViewportMode.PLANET -> PlanetOverviewContent(
                                state = state,
                                onTrade = { viewModel.enterTrade() },
                                onLand = { viewModel.beginLanding(animate = !reduceMotion) }
                            )
                            HaulonautViewportMode.DOCKING -> DockingContent(state)
                            HaulonautViewportMode.DOCKED -> DockedContent(
                                state = state,
                                onExitCraft = { viewModel.exitCraft() },
                                onLaunch = { viewModel.launch() }
                            )
                            // Handled by the outer `when` -- never reached here.
                            HaulonautViewportMode.SURFACE -> {}
                        }
                    }
                    // No bottom bar during the descent transition -- nothing to do.
                    if (state.viewportMode != HaulonautViewportMode.DOCKING) {
                        HaulonautBottomBar(
                            state = state,
                            onVisitOutpost = { viewModel.visitOutpost() },
                            onPlanetOverview = { viewModel.planetOverview() },
                            onViewCargo = { viewModel.viewCargo() },
                            onViewCharts = { viewModel.viewStarCharts() },
                            onViewBuoys = { viewModel.viewBuoys() },
                            onViewComms = { viewModel.openComms() },
                            onBackToSector = { viewModel.exitViewportOverlay() },
                            onWarp = { viewModel.navigate(it) },
                            onAbortAutopilot = { viewModel.abortAutopilot() }
                        )
                    }
                }
            }
        }
    }
}

// Speaker icon + popup with two independent volume sliders and mute toggles (SFX,
// ambient) -- mirrors web's control surface (separate mute buttons + sliders for both
// buses), not iPhone's single combined control, matching HaulonautSoundService's two-bus
// design. The service's mute/volume are plain vars over SharedPreferences rather than
// observable state, so this composable keeps its own mirrored state and writes through to
// the service on every change.
@Composable
private fun HaulonautSoundControls() {
    var expanded by remember { mutableStateOf(false) }
    var soundMuted by remember { mutableStateOf(HaulonautSoundService.soundMuted) }
    var soundVolume by remember { mutableFloatStateOf(HaulonautSoundService.soundVolume) }
    var ambientMuted by remember { mutableStateOf(HaulonautSoundService.ambientMuted) }
    var ambientVolume by remember { mutableFloatStateOf(HaulonautSoundService.ambientVolume) }

    IconButton(
        onClick = { expanded = true },
        modifier = Modifier.semantics { contentDescription = "Sound settings" }
    ) {
        Icon(
            if (soundMuted && ambientMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = null
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(240.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Sound Effects", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = !soundMuted,
                    onCheckedChange = {
                        soundMuted = !it
                        HaulonautSoundService.soundMuted = soundMuted
                    },
                    modifier = Modifier.testTag("haulonaut-sfx-mute-switch")
                )
            }
            Slider(
                value = soundVolume,
                onValueChange = {
                    soundVolume = it
                    HaulonautSoundService.soundVolume = it
                },
                enabled = !soundMuted,
                modifier = Modifier.testTag("haulonaut-sfx-volume-slider")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Ambience", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = !ambientMuted,
                    onCheckedChange = {
                        ambientMuted = !it
                        HaulonautSoundService.ambientMuted = ambientMuted
                    },
                    modifier = Modifier.testTag("haulonaut-ambient-mute-switch")
                )
            }
            Slider(
                value = ambientVolume,
                onValueChange = {
                    ambientVolume = it
                    HaulonautSoundService.ambientVolume = it
                },
                enabled = !ambientMuted,
                modifier = Modifier.testTag("haulonaut-ambient-volume-slider")
            )
        }
    }
}

// Full-screen end state -- see the `state.dead ->` branch. Mirrors web's .lost-screen.
@Composable
private fun PilotLostContent(name: String, onExit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp).testTag("haulonaut-pilot-lost"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "PILOT LOST",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        if (name.isNotBlank()) Text(name, style = MaterialTheme.typography.titleMedium)
        Text(
            "The crew ran out of rations and did not survive the next jump.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onExit, modifier = Modifier.testTag("haulonaut-pilot-lost-exit-btn")) {
            Text("Back to Games")
        }
    }
}

// Secondary HUD strip below the app bar for the two stats that carry richer visuals than
// a plain pill: the crew health meter and the cycle budget with its replenish countdown.
// Credits/Rations/Fuel/Drift stay in the app bar (see the TopAppBar actions).
@Composable
private fun HaulonautStatusHud(state: HaulonautPlayUiState) {
    if (state.character == null) return
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HealthMeter(health = state.health, low = state.healthLow, critical = state.healthCritical)
            CyclesStat(
                // Red when a warp is unaffordable (the ship view's primary maneuver),
                // not just at a bare zero -- matches web's `:empty="!canAffordWarp"`.
                displayed = state.displayedCycles,
                out = !state.canAffordWarp,
                countdown = state.cycleCountdownLabel
            )
            if (state.rationsEmpty && !state.dead) {
                Text(
                    "Larder empty — warping costs health",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun HealthMeter(health: Int, low: Boolean, critical: Boolean) {
    // Green -> amber -> red, same "colour is a supplementary cue" posture as the empty
    // resource pills. The numeric value and the bar length are the primary signals.
    val barColor = when {
        critical -> MaterialTheme.colorScheme.error
        low -> Color(0xFFF9A825)
        else -> Color(0xFF2E7D32)
    }
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.semantics {
            contentDescription = "Crew health $health of $MAX_HEALTH" +
                if (critical) ", critical" else if (low) ", low" else ""
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$health/$MAX_HEALTH",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("haulonaut-resource-health")
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(health.coerceIn(0, MAX_HEALTH) / MAX_HEALTH.toFloat())
                        .background(barColor)
                )
            }
        }
        Text("Health", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CyclesStat(displayed: Int, out: Boolean, countdown: String) {
    val color = if (out) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.semantics {
            contentDescription = "$displayed of $HAULONAUT_MAX_CYCLES cycles" +
                if (countdown.isNotEmpty()) ", next in $countdown" else ""
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$displayed/$HAULONAUT_MAX_CYCLES",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.testTag("haulonaut-resource-cycles")
            )
            if (countdown.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "+1 in $countdown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text("Cycles", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// `tagKey` is the stable test-hook identity, kept separate from the visible `label` so
// the "Credits" -> "Tokens" display rename (web parity) doesn't move the resource-id
// BreakTest reads (haulonaut-resource-credits). Defaults to the lowercased label for the
// pills whose word never changed.
@Composable
private fun ResourcePill(label: String, value: Int, tagKey: String = label.lowercase()) {
    val color = if (value <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    // Deliberately no mergeDescendants/contentDescription here -- an earlier attempt at
    // merging "<value> <label>" into one TalkBack stop made the value untestable (Appium's
    // UiAutomator2 driver reads the merged AccessibilityNodeInfo tree, and the child Text's
    // own node -- and its testTag-mapped resource-id -- stopped exposing text/content-desc
    // once merged into this Column). Two TalkBack stops per pill is an acceptable trade for
    // a reliably-readable value.
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.testTag("haulonaut-resource-$tagKey")
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Only ever shown while driftEligible -- the "Drift" label and the number climbing are
// the primary cues, with the error-color blink as emphasis on top rather than the only
// signal. Blink target collapses to a no-op range under Reduce Motion instead of skipping
// the animation object entirely, so this stays a single unconditional composable call.
@Composable
private fun DriftVariancePill(value: Int) {
    val reduceMotion = isReduceMotionEnabled()
    val infiniteTransition = rememberInfiniteTransition(label = "drift-blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (reduceMotion) 1f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift-alpha"
    )
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.alpha(alpha).testTag("haulonaut-resource-driftvariance")
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = "Drift",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SpaceSceneContent(state: HaulonautPlayUiState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SECTOR ${state.currentSector?.sector_number ?: "—"}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("haulonaut-sector-number")
        )

        if (state.planetFeature != null || state.outpostFeature != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                state.planetFeature?.let { planet ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PlanetSphere(name = planet.name, size = 72.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(planet.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
                state.outpostFeature?.let { outpost ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(outpost.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        state.currentSector?.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.playersHere.isNotEmpty()) {
            Column {
                Text(
                    text = "Pilots here",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                state.playersHere.forEach { player ->
                    Text(
                        text = player.display_name + if (player.isNpc) " [NPC]" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun OutpostContent(
    state: HaulonautPlayUiState,
    onPurchase: (HaulonautItem) -> Unit
) {
    // Only ~1/3 of trading_outpost/planet features stock probes (see sells_probe,
    // migration 074) -- re-checked here the same way the server re-checks it, since the
    // catalog itself (GET /items) is global, not sector-specific.
    val sellsProbe = state.features.any {
        (it.feature_type == "trading_outpost" || it.feature_type == "planet") && it.sells_probe
    }
    val visibleCatalog = state.itemsCatalog.filter { it.item_key != "probe" || sellsProbe }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = (state.outpostFeature?.name ?: "Trading Outpost").uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (visibleCatalog.isEmpty()) {
            Text(
                "Nothing for sale right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            visibleCatalog.forEach { item ->
                val owned = state.inventoryQuantity(item.item_key)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Medium)
                            val ownedSuffix = if (owned > 0) " · owned $owned" else ""
                            Text(
                                text = "${item.base_price} Tokens$ownedSuffix",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { onPurchase(item) },
                            enabled = !state.isPurchasing && state.credits >= item.base_price,
                            modifier = Modifier
                                .testTag("haulonaut-outpost-buy-${item.item_key}")
                                .semantics { contentDescription = "Buy ${item.name} for ${item.base_price} Tokens" }
                        ) {
                            Text("Buy")
                        }
                    }
                }
            }
        }
    }
}

private val PROBE_MISSION_TYPES = listOf(
    "explore" to "Explore Undiscovered Space",
    "search" to "Search For Something",
    "traders" to "Find Other Traders"
)

@Composable
private fun CargoContent(
    state: HaulonautPlayUiState,
    onDeployProbe: (String, String?) -> Unit,
    onDropBuoy: () -> Unit
) {
    var showMissionPicker by remember { mutableStateOf(false) }
    var showSearchPicker by remember { mutableStateOf(false) }

    val probeEntry = state.inventory.firstOrNull { it.item_key == "probe" }
    val canDeployProbe = state.activeProbe == null && (probeEntry?.quantity ?: 0) > 0

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("CARGO MANIFEST", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (state.inventory.isEmpty()) {
            Text(
                "Cargo hold is empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.inventory.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(entry.name)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("×${entry.quantity}", fontWeight = FontWeight.Bold)
                        if (entry.item_key == "probe" && canDeployProbe) {
                            Button(
                                onClick = { showMissionPicker = true },
                                enabled = !state.isDeployingProbe,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("haulonaut-cargo-deploy-probe")
                            ) {
                                Text("Deploy", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (entry.item_key == "tracking_buoy") {
                            Button(
                                onClick = onDropBuoy,
                                enabled = !state.isDroppingBuoy,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("haulonaut-cargo-drop-buoy")
                            ) {
                                Text("Drop", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        // An active mission is a standalone row (not tied to an inventory entry): the
        // probe was already consumed from cargo the moment it deployed.
        state.activeProbe?.let { probe ->
            HorizontalDivider()
            val label = PROBE_MISSION_TYPES.firstOrNull { it.first == probe.mission_type }?.second ?: probe.mission_type
            Text("PROBE EN ROUTE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = probe.progress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showMissionPicker) {
        AlertDialog(
            onDismissRequest = { showMissionPicker = false },
            title = { Text("Deploy Recon Probe") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PROBE_MISSION_TYPES.forEach { (type, label) ->
                        TextButton(
                            onClick = {
                                showMissionPicker = false
                                if (type == "search") {
                                    showSearchPicker = true
                                } else {
                                    onDeployProbe(type, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMissionPicker = false }) { Text("Cancel") } }
        )
    }

    if (showSearchPicker) {
        val searchableItems = state.itemsCatalog.filter { it.item_key != "probe" }
        AlertDialog(
            onDismissRequest = { showSearchPicker = false },
            title = { Text("Search For What?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (searchableItems.isEmpty()) {
                        Text("No known items to search for.", style = MaterialTheme.typography.bodyMedium)
                    }
                    searchableItems.forEach { item ->
                        TextButton(
                            onClick = {
                                showSearchPicker = false
                                onDeployProbe("search", item.item_key)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(item.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSearchPicker = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ChartsContent(
    state: HaulonautPlayUiState,
    onSetCourse: (HaulonautKnownLocation) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("STAR CHARTS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Known locations — tap to plot a course",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.isLoadingCharts) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        } else if (state.knownLocations.isEmpty()) {
            Text(
                "No known locations yet — explore more sectors.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.knownLocations.forEach { location ->
                val isHere = location.distance == 0
                val icon = when (location.feature_type) {
                    "planet" -> Icons.Default.Public
                    "trading_outpost" -> Icons.Default.ShoppingCart
                    else -> Icons.Default.Star
                }
                val distanceLabel = if (isHere) {
                    "HERE"
                } else {
                    val hops = location.distance ?: 0
                    "Sector ${location.sector_number} · $hops hop${if (hops == 1) "" else "s"} away"
                }
                Card(
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "${location.name}, ${if (isHere) "current location" else "$distanceLabel, tap to set course"}"
                    },
                    onClick = { if (!isHere && !state.isTraveling) onSetCourse(location) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(location.name, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            text = distanceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isHere) FontWeight.Bold else FontWeight.Normal,
                            color = if (isHere) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Magnetic Tracking Buoys -- read-only status (like Star Charts), just a list of
// dropped/attached buoys this pilot owns. Nothing in the list is clickable; there's no
// action to take here beyond closing the overlay.
@Composable
private fun BuoysContent(state: HaulonautPlayUiState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("TRACKING BUOYS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Buoys dropped from Cargo report a ship's location once one catches a pilot.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.isLoadingBuoys) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        } else if (state.buoys.isEmpty()) {
            Text(
                "No tracking buoys deployed yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.buoys.forEach { buoy ->
                val attached = buoy.status == "attached"
                val statusLabel = if (attached) "ATTACHED — ${buoy.targetDisplayName}" else "AWAITING CONTACT"
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                statusLabel,
                                fontWeight = FontWeight.Medium,
                                color = if (attached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = buoy.sectorNumber?.let { "Sector $it" } ?: "Location unknown",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (attached) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                            contentDescription = null,
                            tint = if (attached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// 24-hour, time only -- see HaulonautCommsLine. Only ever touched from the main thread.
private val COMMS_TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

// Sector comms -- the running radio log plus a command input. Anything typed goes out to
// every other pilot in this sector unless it starts with '/', in which case it's a
// command (/give, /offer, /accept, /decline, /attack). Mirrors web's TERMINAL panel.
@Composable
private fun CommsContent(
    state: HaulonautPlayUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val scrollState = rememberScrollState()
    // Keep the newest line in view as the log grows.
    LaunchedEffect(state.commsLog.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp).testTag("haulonaut-comms"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("SECTOR COMMS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .verticalScroll(scrollState)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (state.commsLog.isEmpty()) {
                Text(
                    "Channel quiet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.commsLog.forEach { line ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = "> ${line.text}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = COMMS_TIME_FORMAT.format(Date(line.atMs)),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = state.commsInput,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth().testTag("haulonaut-comms-input"),
            singleLine = true,
            placeholder = { Text("Broadcast, or /give /offer /accept /decline /attack") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            trailingIcon = {
                IconButton(
                    onClick = onSubmit,
                    enabled = state.commsInput.isNotBlank(),
                    modifier = Modifier.testTag("haulonaut-comms-send-btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        )
    }
}

// Planet Overview menu -- Trade (reuses the outpost view/flow) or Land (the simple
// descent transition, then the docked screen). Mirrors web's planetMenuItems.
@Composable
private fun PlanetOverviewContent(
    state: HaulonautPlayUiState,
    onTrade: () -> Unit,
    onLand: () -> Unit
) {
    val planet = state.planetFeature
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlanetSphere(name = planet?.name ?: "Planet", size = 96.dp)
        Text(
            text = (planet?.name ?: "PLANET").uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        planet?.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onTrade,
            modifier = Modifier.fillMaxWidth().testTag("haulonaut-planet-trade-btn")
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Trade")
        }
        val landBlocked = !state.canAffordLanding
        Button(
            onClick = onLand,
            enabled = !landBlocked && !state.isDocking,
            modifier = Modifier.fillMaxWidth().testTag("haulonaut-planet-land-btn")
        ) {
            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Land ($DOCK_CYCLE_COST cycles)")
        }
        if (landBlocked) {
            Text(
                "Landing needs $DOCK_CYCLE_COST cycles — ${state.displayedCycles} available. Ready in ${state.landingReadyLabel}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// The "simple transition" descent -- a brief spinner while POST /dock runs. No montage.
@Composable
private fun DockingContent(state: HaulonautPlayUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("haulonaut-docking"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlanetSphere(name = state.planetFeature?.name ?: "Planet", size = 140.dp)
        CircularProgressIndicator()
        Text(
            "Descending toward ${state.planetFeature?.name ?: "the surface"}…",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// Landed, still aboard the ship. Exit Craft (Phase 3 surface expedition) or Launch to
// undock. Mirrors web's docked landing-sequence menu.
@Composable
private fun DockedContent(
    state: HaulonautPlayUiState,
    onExitCraft: () -> Unit,
    onLaunch: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("haulonaut-docked"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlanetSphere(name = state.planetFeature?.name ?: "Planet", size = 96.dp)
        Text(
            text = "DOCKED AT ${(state.planetFeature?.name ?: "PLANET").uppercase()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Docking clamps engaged. The landing facility is secure.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onExitCraft,
            modifier = Modifier.fillMaxWidth().testTag("haulonaut-exit-craft-btn")
        ) {
            Text("Exit Craft")
        }
        OutlinedButton(
            onClick = onLaunch,
            modifier = Modifier.fillMaxWidth().testTag("haulonaut-launch-btn")
        ) {
            Text("Launch")
        }
    }
}

// The deterministic-hue sphere the space scene already uses for a planet, factored out
// so the planet-overview / docking / docked screens render the same body.
@Composable
private fun PlanetSphere(name: String, size: androidx.compose.ui.unit.Dp) {
    val hue = hashHue(name)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color.hsv(hue, 0.55f, 0.85f),
                        Color.hsv(hue, 0.7f, 0.35f)
                    )
                )
            )
    )
}

// The planet surface -- a deliberately simpler paradigm than the ship view it replaces
// (see the SURFACE branch in the main when). A sky-hued backdrop, the buggy, a
// fog-of-war minimap, the landing-event log, and a D-pad. Mirrors web's .surface-screen,
// minus the Oregon-Trail parallax side-view (a follow-up).
@Composable
private fun SurfaceContent(
    state: HaulonautPlayUiState,
    onDrive: (String) -> Unit,
    onReturnToShip: () -> Unit
) {
    val planetName = state.planetFeature?.name ?: "PLANET SURFACE"
    val hue = hashHue(state.planetFeature?.name ?: "Planet")
    val map = state.surfaceMap
    // Terrain tone drifts a little with latitude (buggyY) so different rows at least look
    // like different areas -- always dirt-brown, never tied to the sky hue (web parity).
    val terrainLightness = 0.14f + ((map?.buggyY ?: 0) % 4) * 0.04f

    Column(
        modifier = Modifier.fillMaxSize().testTag("haulonaut-surface")
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.hsv(hue, 0.35f, 0.82f),
                            Color.hsv(hue, 0.30f, 0.60f)
                        )
                    )
                )
        ) {
            // Ground band
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.hsv(28f, 0.38f, terrainLightness + 0.06f),
                                Color.hsv(28f, 0.38f, (terrainLightness - 0.04f).coerceAtLeast(0.04f))
                            )
                        )
                    )
            )
            // The buggy sits fixed near the middle; the ship marker joins it when parked.
            Row(
                modifier = Modifier.align(Alignment.Center).padding(bottom = 24.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.buggyAtShip) Text("🚀", style = MaterialTheme.typography.displaySmall) // rocket
                Text("🚙", style = MaterialTheme.typography.displayMedium) // buggy
            }

            SurfaceMinimap(
                map = map,
                revealed = state.revealedSet,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            )
        }

        Surface(tonalElevation = 2.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(planetName.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SurfaceStat("Tokens", state.credits.toString())
                    SurfaceStat("Rations", state.rations.toString(), warn = state.rationsEmpty)
                    SurfaceStat("Fuel", state.fuel.toString(), warn = state.fuel <= 0)
                    SurfaceStat("Health", "${state.health}/$MAX_HEALTH", warn = state.healthCritical)
                    SurfaceStat(
                        "Cycles",
                        "${state.displayedCycles}/$HAULONAUT_MAX_CYCLES" +
                            if (state.cycleCountdownLabel.isNotEmpty()) "  +1 in ${state.cycleCountdownLabel}" else "",
                        warn = state.outOfCycles
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val recent = state.surfaceLog.takeLast(4)
                if (recent.isEmpty()) {
                    Text(
                        "The surface is still and silent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    recent.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        SurfaceControls(
            enabled = !state.isBuggyMoving && !state.outOfCycles,
            outOfCycles = state.outOfCycles,
            cycleCountdown = state.cycleCountdownLabel,
            showDock = state.buggyAtShip,
            onDrive = onDrive,
            onReturnToShip = onReturnToShip
        )
    }
}

@Composable
private fun SurfaceStat(label: String, value: String, warn: Boolean = false) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Fog-of-war grid: revealed cells lit, the rest dark. Row-major index = y * gridWidth + x,
// matching haulonaut_surface_maps.revealed_cells.
@Composable
private fun SurfaceMinimap(map: HaulonautSurfaceMap?, revealed: Set<Int>, modifier: Modifier = Modifier) {
    if (map == null) {
        Text("Charting surface…", style = MaterialTheme.typography.labelSmall, modifier = modifier)
        return
    }
    val lit = MaterialTheme.colorScheme.surfaceVariant
    val fog = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
            .padding(3.dp)
            .testTag("haulonaut-surface-minimap"),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        for (y in 0 until map.gridHeight) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (x in 0 until map.gridWidth) {
                    val idx = y * map.gridWidth + x
                    val isShip = x == map.shipX && y == map.shipY
                    val isBuggy = x == map.buggyX && y == map.buggyY
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .background(if (revealed.contains(idx)) lit else fog),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isBuggy -> Text("●", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            isShip -> Text("▲", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceControls(
    enabled: Boolean,
    outOfCycles: Boolean,
    cycleCountdown: String,
    showDock: Boolean,
    onDrive: (String) -> Unit,
    onReturnToShip: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onDrive("up") },
                enabled = enabled,
                modifier = Modifier.testTag("haulonaut-buggy-north")
            ) { Text("North") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onDrive("left") },
                    enabled = enabled,
                    modifier = Modifier.testTag("haulonaut-buggy-west")
                ) { Text("West") }
                if (showDock) {
                    Button(
                        onClick = onReturnToShip,
                        modifier = Modifier.testTag("haulonaut-return-to-ship-btn")
                    ) { Text("Dock with Ship") }
                } else {
                    Spacer(modifier = Modifier.width(96.dp))
                }
                OutlinedButton(
                    onClick = { onDrive("right") },
                    enabled = enabled,
                    modifier = Modifier.testTag("haulonaut-buggy-east")
                ) { Text("East") }
            }
            OutlinedButton(
                onClick = { onDrive("down") },
                enabled = enabled,
                modifier = Modifier.testTag("haulonaut-buggy-south")
            ) { Text("South") }
            if (outOfCycles) {
                Text(
                    "Out of cycles — the buggy is parked. +1 in $cycleCountdown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun HaulonautBottomBar(
    state: HaulonautPlayUiState,
    onVisitOutpost: () -> Unit,
    onPlanetOverview: () -> Unit,
    onViewCargo: () -> Unit,
    onViewCharts: () -> Unit,
    onViewBuoys: () -> Unit,
    onViewComms: () -> Unit,
    onBackToSector: () -> Unit,
    onWarp: (HaulonautConnectedSector) -> Unit,
    onAbortAutopilot: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (state.isTraveling) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AUTOPILOT: ${state.travelDestinationName ?: "En route"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${state.travelHopsRemaining} hop${if (state.travelHopsRemaining == 1) "" else "s"} remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onAbortAutopilot, modifier = Modifier.testTag("haulonaut-abort-autopilot-btn")) {
                        Text("Abort", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (state.viewportMode == HaulonautViewportMode.SPACE) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.outpostFeature != null) {
                        AssistChip(
                            onClick = onVisitOutpost,
                            leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                            label = { Text("Visit Outpost") },
                            modifier = Modifier.testTag("haulonaut-visit-outpost-btn")
                        )
                    }
                    if (state.planetFeature != null) {
                        AssistChip(
                            onClick = onPlanetOverview,
                            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                            label = { Text("Planet Overview") },
                            modifier = Modifier.testTag("haulonaut-planet-overview-btn")
                        )
                    }
                    AssistChip(
                        onClick = onViewCargo,
                        leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                        label = { Text("Cargo") },
                        modifier = Modifier.testTag("haulonaut-view-cargo-btn")
                    )
                    AssistChip(
                        onClick = onViewCharts,
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                        label = { Text("Star Charts") },
                        modifier = Modifier.testTag("haulonaut-view-charts-btn")
                    )
                    AssistChip(
                        onClick = onViewBuoys,
                        leadingIcon = { Icon(Icons.Default.GpsFixed, contentDescription = null) },
                        label = { Text("Buoys") },
                        modifier = Modifier.testTag("haulonaut-view-buoys-btn")
                    )
                    AssistChip(
                        onClick = onViewComms,
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                        label = { Text("Comms") },
                        modifier = Modifier.testTag("haulonaut-view-comms-btn")
                    )
                }
            } else if (state.viewportMode != HaulonautViewportMode.DOCKED) {
                // DOCKED has its own Launch button in the content area; every other
                // overlay (OUTPOST/CARGO/CHARTS/PLANET) backs out to the sector here.
                Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = onBackToSector, modifier = Modifier.testTag("haulonaut-back-to-sector-btn")) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (state.viewportMode == HaulonautViewportMode.PLANET) "Break Orbit" else "Back to Sector")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WARP TO ($WARP_CYCLE_COST cycles)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                if (state.connectedSectors.isEmpty()) {
                    Text(
                        "no warps available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.connectedSectors.forEach { sector ->
                            val label = if (sector.visited) "${sector.sector_number} ✓" else "${sector.sector_number}"
                            OutlinedButton(
                                onClick = { onWarp(sector) },
                                enabled = !state.isNavigating && state.canAffordWarp,
                                modifier = Modifier
                                    .testTag("haulonaut-warp-btn-${sector.sector_number}")
                                    .semantics {
                                        contentDescription = "Warp to Sector ${sector.sector_number}" +
                                            if (sector.visited) ", visited" else ", unexplored"
                                    }
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
            if (!state.canAffordWarp && state.connectedSectors.isNotEmpty()) {
                Text(
                    text = "Warp needs $WARP_CYCLE_COST cycles — ${state.displayedCycles} available. Ready in ${state.warpReadyLabel}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
    }
}
