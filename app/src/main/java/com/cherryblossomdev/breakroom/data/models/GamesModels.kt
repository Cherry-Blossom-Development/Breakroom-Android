package com.cherryblossomdev.breakroom.data.models

// ==================== Games / Haulonaut models ====================
// Mirrors backend/routes/games.js response shapes exactly (see that file for the
// authoritative field list -- these are plain data-carrying mirrors, not independently
// designed).

// Every action endpoint (navigate, drift, purchase, dock, drive-buggy, exit-craft) and
// the character snapshot re-send the pilot's cycle balance + accrual anchor alongside
// credits/rations/fuel/health -- see backend migrations 066 (cycles) and 067 (health).
// The ViewModel folds whichever response it gets through applyPilotState().
interface HaulonautPilotState {
    val credits: Int
    val rations: Int
    val fuel: Int
    val health: Int
    // Server's last-known cycle balance and the epoch-seconds anchor its lazy
    // replenishment (one cycle per real hour, cap 24) is measured from. The client
    // re-runs that same math locally against wall-clock time so the HUD ticks up
    // between the syncs every real action already does.
    val cycles: Int
    val cyclesUpdatedAt: Long
}

data class HaulonautGame(
    val id: Int,
    val game_key: String,
    val name: String,
    val description: String? = null
)

data class HaulonautInstance(
    val id: Int,
    val name: String,
    val started_at: String? = null,
    val sector_count: Int = 0,
    val player_count: Int = 0
)

// instance_id/instance_name/instance_status are only populated when this character
// came back as part of the GET /:gameKey characters list; absent from the
// create-character and single-character-fetch responses.
data class HaulonautCharacter(
    val id: Int,
    val display_name: String,
    val status: String,
    val created_at: String? = null,
    val last_played_at: String? = null,
    val died_at: String? = null,
    val instance_id: Int? = null,
    val instance_name: String? = null,
    val instance_status: String? = null
)

data class HaulonautSector(
    val id: Int,
    val sector_number: Int,
    val description: String? = null
)

data class HaulonautConnectedSector(
    val id: Int,
    val sector_number: Int,
    val visited: Boolean = false
)

data class HaulonautSectorFeature(
    val id: Int,
    val feature_type: String,
    val name: String,
    val description: String? = null
)

data class HaulonautPlayerHere(
    val id: Int,
    val display_name: String
)

// Owned quantity of an item -- rations never appear here, they're a top-level pilot stat.
data class HaulonautInventoryItem(
    val item_key: String,
    val name: String,
    val category: String,
    val quantity: Int
)

// Catalog entry (GET /items) -- distinct from HaulonautInventoryItem, which is what a
// character owns.
data class HaulonautItem(
    val id: Int,
    val item_key: String,
    val name: String,
    val category: String,
    val description: String? = null,
    val base_price: Int
)

data class HaulonautGameInfoResponse(
    val game: HaulonautGame,
    val instances: List<HaulonautInstance> = emptyList(),
    val characters: List<HaulonautCharacter> = emptyList(),
    val isAdmin: Boolean = false
)

data class HaulonautCreateCharacterRequest(
    val display_name: String,
    val instance_id: Int
)

data class HaulonautCreateCharacterResponse(
    val character: HaulonautCharacter
)

data class HaulonautCharacterSnapshotResponse(
    val character: HaulonautCharacter,
    val currentSector: HaulonautSector? = null,
    val connectedSectors: List<HaulonautConnectedSector> = emptyList(),
    val features: List<HaulonautSectorFeature> = emptyList(),
    val playersHere: List<HaulonautPlayerHere> = emptyList(),
    override val credits: Int = 0,
    override val rations: Int = 0,
    override val fuel: Int = 0,
    override val health: Int = 100,
    override val cycles: Int = 0,
    override val cyclesUpdatedAt: Long = 0,
    val inventory: List<HaulonautInventoryItem> = emptyList()
) : HaulonautPilotState

data class HaulonautNavigateRequest(
    val to_sector_id: Int
)

data class HaulonautNavigateResponse(
    val currentSector: HaulonautSector? = null,
    val connectedSectors: List<HaulonautConnectedSector> = emptyList(),
    val features: List<HaulonautSectorFeature> = emptyList(),
    val playersHere: List<HaulonautPlayerHere> = emptyList(),
    override val credits: Int = 0,
    override val rations: Int = 0,
    override val fuel: Int = 0,
    override val health: Int = 100,
    override val cycles: Int = 0,
    override val cyclesUpdatedAt: Long = 0,
    // Present and true only when this warp's starvation damage just dropped crew
    // health to 0 (game_users.status -> 'dead').
    val died: Boolean = false
) : HaulonautPilotState

data class HaulonautItemsResponse(
    val items: List<HaulonautItem> = emptyList()
)

data class HaulonautPurchaseRequest(
    val item_key: String,
    val quantity: Int = 1
)

data class HaulonautPurchaseResponse(
    val message: String,
    override val credits: Int,
    override val rations: Int,
    override val fuel: Int = 0,
    override val health: Int = 100,
    override val cycles: Int = 0,
    override val cyclesUpdatedAt: Long = 0,
    val inventory: List<HaulonautInventoryItem> = emptyList()
) : HaulonautPilotState

// GET /characters/:id/cycles -- a lightweight re-sync (no full character reload) polled
// on resume so a session left backgrounded for hours picks up the wall-clock
// replenishment the server accrued the whole time. Returns ONLY the cycle fields (no
// credits/rations/fuel/health), so it deliberately does not implement HaulonautPilotState.
data class HaulonautCyclesResponse(
    val cycles: Int = 0,
    val cyclesUpdatedAt: Long = 0,
    val maxCycles: Int = 24,
    val replenishSeconds: Int = 3600
)

// Same shape as HaulonautNavigateResponse -- drift moves the character exactly like a
// warp, just without touching credits/rations/fuel (see backend/routes/games.js's
// POST .../drift for the "why").
data class HaulonautDriftResponse(
    val currentSector: HaulonautSector? = null,
    val connectedSectors: List<HaulonautConnectedSector> = emptyList(),
    val features: List<HaulonautSectorFeature> = emptyList(),
    val playersHere: List<HaulonautPlayerHere> = emptyList(),
    override val credits: Int = 0,
    override val rations: Int = 0,
    override val fuel: Int = 0,
    override val health: Int = 100,
    override val cycles: Int = 0,
    override val cyclesUpdatedAt: Long = 0
) : HaulonautPilotState

// ==================== Star Charts models ====================

// A discovered sector feature (planet, trading_outpost, ...) annotated with its
// hop-distance from the character's current sector. distance == 0 means the
// character is already standing there.
data class HaulonautKnownLocation(
    val id: Int,
    val feature_type: String,
    val name: String,
    val description: String? = null,
    val sector_id: Int,
    val sector_number: Int,
    val distance: Int? = null
)

data class HaulonautKnownLocationsResponse(
    val locations: List<HaulonautKnownLocation> = emptyList()
)

data class HaulonautRouteWaypoint(
    val id: Int,
    val sector_number: Int
)

data class HaulonautRouteResponse(
    val path: List<HaulonautRouteWaypoint> = emptyList()
)
