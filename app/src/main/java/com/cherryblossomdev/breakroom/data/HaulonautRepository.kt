package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.BreakroomApiService
import com.cherryblossomdev.breakroom.network.ErrorResponse
import com.google.gson.Gson

private const val GAME_KEY = "haulonaut"

class HaulonautRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    private fun getAuthHeader(): String? = tokenManager.getBearerToken()

    suspend fun getGameInfo(): BreakroomResult<HaulonautGameInfoResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getGameInfo(auth, GAME_KEY)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No game data")
            } else {
                BreakroomResult.Error("Failed to load game")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createCharacter(displayName: String, instanceId: Int): BreakroomResult<HaulonautCharacter> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = HaulonautCreateCharacterRequest(display_name = displayName, instance_id = instanceId)
            val response = apiService.createHaulonautCharacter(auth, GAME_KEY, request)
            if (response.isSuccessful) {
                response.body()?.character?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No character data")
            } else {
                BreakroomResult.Error("Failed to create character")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getCharacter(characterId: Int): BreakroomResult<HaulonautCharacterSnapshotResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getHaulonautCharacter(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No character data")
            } else {
                BreakroomResult.Error("Failed to load character")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun navigate(characterId: Int, toSectorId: Int): BreakroomResult<HaulonautNavigateResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = HaulonautNavigateRequest(to_sector_id = toSectorId)
            val response = apiService.navigateHaulonautCharacter(auth, GAME_KEY, characterId, request)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No navigation data")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "That sector is not reachable from here")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getItems(): BreakroomResult<List<HaulonautItem>> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getHaulonautItems(auth, GAME_KEY)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.items ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load items")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun purchase(characterId: Int, itemKey: String, quantity: Int = 1): BreakroomResult<HaulonautPurchaseResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = HaulonautPurchaseRequest(item_key = itemKey, quantity = quantity)
            val response = apiService.purchaseHaulonautItem(auth, GAME_KEY, characterId, request)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No purchase data")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Purchase failed")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Lightweight cycle re-sync -- no full character reload. Failure is non-fatal: the
    // client's local countdown keeps running off the last known anchor.
    suspend fun getCycles(characterId: Int): BreakroomResult<HaulonautCyclesResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getHaulonautCycles(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No cycle data")
            } else {
                BreakroomResult.Error("Failed to load cycles")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Persists "landed at this planet" once the (client-side) descent completes. The
    // planet is derived server-side from the current sector -- no body needed. Idempotent;
    // only a landing that changes the docked planet spends a cycle.
    suspend fun dock(characterId: Int): BreakroomResult<HaulonautDockResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.dockHaulonautCharacter(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No dock data")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Docking failed")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Undocks -- clears docked_feature_id so the ship shows as back in open space without
    // needing to warp anywhere. Requires being aboard the ship (not out on the surface).
    suspend fun launch(characterId: Int): BreakroomResult<HaulonautActionAck> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.launchHaulonautCharacter(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body() ?: HaulonautActionAck(success = true))
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Launch failed")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getKnownLocations(characterId: Int): BreakroomResult<List<HaulonautKnownLocation>> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getHaulonautKnownLocations(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.locations ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load star charts")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getRoute(characterId: Int, sectorId: Int): BreakroomResult<List<HaulonautRouteWaypoint>> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getHaulonautRoute(auth, GAME_KEY, characterId, sectorId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.path ?: emptyList())
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to plot course")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Rejected with a specific message (e.g. "Not out of fuel", "Already at a planet")
    // when drift doesn't apply right now -- callers treat that as a quiet no-op, same as
    // web's `if (!res.ok) return`, since the next tick will just re-check eligibility.
    suspend fun drift(characterId: Int): BreakroomResult<HaulonautDriftResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.driftHaulonautCharacter(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No drift data")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Drift failed")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}

// The backend returns a specific {message} on 4xx here (e.g. "Not enough credits",
// "That sector is not reachable from here") that's worth surfacing over a generic string.
private fun retrofit2.Response<*>.errorBodyMessage(): String? = try {
    Gson().fromJson(errorBody()?.string(), ErrorResponse::class.java)?.message
} catch (e: Exception) {
    null
}
