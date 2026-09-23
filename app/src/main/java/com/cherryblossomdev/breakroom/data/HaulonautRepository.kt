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

    // Steps onto the planet surface -- requires already being docked. Returns (creating on
    // a first-ever visit) that planet's surface map.
    suspend fun exitCraft(characterId: Int): BreakroomResult<HaulonautExitCraftResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.exitCraftHaulonautCharacter(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No surface data")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to exit craft")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Boards the ship from the surface -- only allowed once the buggy is parked back on
    // the ship's own cell (re-checked server-side). Docked status is left untouched.
    suspend fun returnToShip(characterId: Int): BreakroomResult<HaulonautActionAck> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.returnToShipHaulonautCharacter(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body() ?: HaulonautActionAck(success = true))
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to board ship")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Moves the buggy one cell. A grid-edge bump comes back 200 with the position
    // unchanged and no cycle spent -- callers treat that as a quiet no-op.
    suspend fun driveBuggy(characterId: Int, direction: String): BreakroomResult<HaulonautDriveBuggyResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.driveBuggyHaulonautCharacter(auth, GAME_KEY, characterId, HaulonautDriveBuggyRequest(direction))
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No move data")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Move failed")
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

    // ---- Shared-sector: gifting, trading, combat ----

    // Instant credit gift to another active pilot in the same sector -- no acceptance
    // needed. The recipient hears about it over haulonaut_gift_received.
    suspend fun give(characterId: Int, toCharacterId: Int, credits: Int): BreakroomResult<HaulonautGiveResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.giveHaulonautCredits(auth, GAME_KEY, characterId, HaulonautGiveRequest(toCharacterId, credits))
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) } ?: BreakroomResult.Error("No response")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to give tokens")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Every pending offer involving this character, either direction -- used on load to
    // catch up on offers that arrived while disconnected (the live socket notification
    // only reaches an open session).
    suspend fun getTradeOffers(characterId: Int): BreakroomResult<List<HaulonautTradeOfferSummary>> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getHaulonautTradeOffers(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.offers ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load trade offers")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Proposes selling `quantity` of one owned item to another pilot in the sector for
    // `credits`. Nothing moves until they accept.
    suspend fun createTradeOffer(
        characterId: Int,
        toCharacterId: Int,
        itemKey: String,
        quantity: Int,
        credits: Int
    ): BreakroomResult<HaulonautCreateTradeOfferResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.createHaulonautTradeOffer(
                auth, GAME_KEY, characterId,
                HaulonautTradeOfferRequest(toCharacterId, itemKey, quantity, credits)
            )
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) } ?: BreakroomResult.Error("No response")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to send trade offer")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Accepts a pending offer addressed to this character -- re-validates both sides
    // server-side. Returns the accepter's new balance + inventory.
    suspend fun acceptTradeOffer(characterId: Int, offerId: Int): BreakroomResult<HaulonautTradeAcceptResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.acceptHaulonautTradeOffer(auth, GAME_KEY, characterId, offerId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) } ?: BreakroomResult.Error("No response")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to accept trade offer")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Declines a pending offer addressed to this character.
    suspend fun declineTradeOffer(characterId: Int, offerId: Int): BreakroomResult<HaulonautActionAck> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.declineHaulonautTradeOffer(auth, GAME_KEY, characterId, offerId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body() ?: HaulonautActionAck(success = true))
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to decline trade offer")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Read-only look at what another pilot in this sector is carrying (credits +
    // inventory) -- context for the Hail trade form, not a trade action itself.
    suspend fun getPilotSnapshot(characterId: Int, targetId: Int): BreakroomResult<HaulonautPilotSnapshot> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getHaulonautPilotSnapshot(auth, GAME_KEY, characterId, targetId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) } ?: BreakroomResult.Error("No response")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to load pilot")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Open PvP: deals randomized damage to another active, non-NPC pilot in the sector.
    // Requires a Laser Cannon in cargo and costs cycles. A successful hit's outcome is
    // told to the whole sector over haulonaut_combat_event, so callers stay quiet on
    // success and only surface the errors carried here.
    suspend fun attack(characterId: Int, toCharacterId: Int): BreakroomResult<HaulonautAttackResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.attackHaulonautCharacter(auth, GAME_KEY, characterId, HaulonautAttackRequest(toCharacterId))
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) } ?: BreakroomResult.Error("No response")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Attack failed")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // ---- Recon probes ----

    // Whatever probe state this pilot needs to catch up on: the active mission (if any)
    // and the most recent unacknowledged completed/failed report.
    suspend fun getProbes(characterId: Int): BreakroomResult<HaulonautProbesResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getHaulonautProbes(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No probe data")
            } else {
                BreakroomResult.Error("Failed to load probe status")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Consumes one probe from cargo and starts a mission. searchItemKey is required (and
    // ignored otherwise) for missionType = "search".
    suspend fun deployProbe(
        characterId: Int,
        missionType: String,
        searchItemKey: String? = null
    ): BreakroomResult<HaulonautDeployProbeResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deployHaulonautProbe(
                auth, GAME_KEY, characterId,
                HaulonautDeployProbeRequest(mission_type = missionType, search_item_key = searchItemKey)
            )
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No deploy data")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to deploy probe")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Dismisses a completed/failed mission's report -- GET /probes stops returning it.
    suspend fun acknowledgeProbeReport(characterId: Int, missionId: Int): BreakroomResult<HaulonautActionAck> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.acknowledgeHaulonautProbeReport(auth, GAME_KEY, characterId, missionId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body() ?: HaulonautActionAck(success = true))
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to acknowledge report")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // ---- Magnetic Tracking Buoys ----

    suspend fun getBuoys(characterId: Int): BreakroomResult<HaulonautBuoysResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getHaulonautBuoys(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No buoy data")
            } else {
                BreakroomResult.Error("Failed to load buoy status")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // Consumes one Magnetic Tracking Buoy from Cargo and drops it in the character's
    // current sector.
    suspend fun dropBuoy(characterId: Int): BreakroomResult<HaulonautDropBuoyResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.dropHaulonautBuoy(auth, GAME_KEY, characterId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No drop data")
            } else {
                BreakroomResult.Error(response.errorBodyMessage() ?: "Failed to drop tracking buoy")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}

// The backend returns a specific {message} on 4xx here (e.g. "Not enough tokens",
// "That sector is not reachable from here") that's worth surfacing over a generic string.
private fun retrofit2.Response<*>.errorBodyMessage(): String? = try {
    Gson().fromJson(errorBody()?.string(), ErrorResponse::class.java)?.message
} catch (e: Exception) {
    null
}
