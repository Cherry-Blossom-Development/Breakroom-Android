package com.cherryblossomdev.breakroom.data.models

// ==================== Games / Haulonaut models ====================
// Mirrors backend/routes/games.js response shapes exactly (see that file for the
// authoritative field list -- these are plain data-carrying mirrors, not independently
// designed).

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
    val credits: Int = 0,
    val rations: Int = 0,
    val inventory: List<HaulonautInventoryItem> = emptyList()
)

data class HaulonautNavigateRequest(
    val to_sector_id: Int
)

data class HaulonautNavigateResponse(
    val currentSector: HaulonautSector? = null,
    val connectedSectors: List<HaulonautConnectedSector> = emptyList(),
    val features: List<HaulonautSectorFeature> = emptyList(),
    val playersHere: List<HaulonautPlayerHere> = emptyList(),
    val credits: Int = 0,
    val rations: Int = 0
)

data class HaulonautItemsResponse(
    val items: List<HaulonautItem> = emptyList()
)

data class HaulonautPurchaseRequest(
    val item_key: String,
    val quantity: Int = 1
)

data class HaulonautPurchaseResponse(
    val message: String,
    val credits: Int,
    val rations: Int,
    val inventory: List<HaulonautInventoryItem> = emptyList()
)

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
