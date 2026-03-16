package com.cherryblossomdev.breakroom

import androidx.compose.runtime.mutableStateListOf

/**
 * App-level singleton holding the current user's block list.
 * Uses Compose snapshot state so any composable reading from it will recompose
 * when the list changes. Mirrors the web app's moderationStore.
 */
object ModerationStore {
    private val _blockedUserIds = mutableStateListOf<Int>()

    fun isBlocked(userId: Int): Boolean = _blockedUserIds.contains(userId)

    fun setBlockList(ids: List<Int>) {
        _blockedUserIds.clear()
        _blockedUserIds.addAll(ids)
    }

    fun addBlock(userId: Int) {
        if (!_blockedUserIds.contains(userId)) _blockedUserIds.add(userId)
    }

    fun removeBlock(userId: Int) {
        _blockedUserIds.remove(userId)
    }

    fun clear() {
        _blockedUserIds.clear()
    }
}
