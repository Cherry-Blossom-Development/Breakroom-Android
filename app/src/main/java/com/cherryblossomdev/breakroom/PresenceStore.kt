package com.cherryblossomdev.breakroom

import androidx.compose.runtime.mutableStateOf

/**
 * App-level singleton holding which user ids are currently online. Uses Compose snapshot
 * state so any composable reading isOnline() recomposes as status changes -- mirrors
 * FeaturesStore/ModerationStore's loaded-at-login pattern.
 *
 * Presence is shown wherever a handle appears (Friends list, chat), not just to friends,
 * matching the backend's global presence_update broadcast (see SocketManager.kt) rather
 * than being scoped to the friends relationship.
 */
object PresenceStore {
    private val _online = mutableStateOf<Set<Int>>(emptySet())

    fun isOnline(userId: Int): Boolean = userId in _online.value

    // Full snapshot from GET /api/user/online-ids -- seeded once at login so status is
    // correct immediately, not just after the first live presence_update.
    fun setOnline(userIds: Collection<Int>) {
        _online.value = userIds.toSet()
    }

    // Merges fresher per-user online status from any list load that carries it (e.g. the
    // friends response's is_online) -- overwrites just those ids, leaving the rest of the
    // snapshot untouched.
    fun hydrate(statuses: Map<Int, Boolean>) {
        val current = _online.value.toMutableSet()
        statuses.forEach { (userId, online) ->
            if (online) current.add(userId) else current.remove(userId)
        }
        _online.value = current
    }

    fun onPresenceUpdate(userId: Int, isOnline: Boolean) {
        _online.value = if (isOnline) _online.value + userId else _online.value - userId
    }

    fun clear() {
        _online.value = emptySet()
    }
}
