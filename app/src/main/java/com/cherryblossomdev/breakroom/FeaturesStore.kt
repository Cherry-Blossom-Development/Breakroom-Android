package com.cherryblossomdev.breakroom

import androidx.compose.runtime.mutableStateOf

/**
 * App-level singleton holding the current user's enrolled feature flags (e.g. "games").
 * Uses Compose snapshot state so any composable reading from it will recompose when the
 * set changes. Mirrors the web app's feature-gating (features.has(key)) and ModerationStore's
 * loaded-once-at-login pattern.
 */
object FeaturesStore {
    private val _enabled = mutableStateOf<Set<String>>(emptySet())

    fun has(featureKey: String): Boolean = featureKey in _enabled.value

    fun setEnabled(keys: List<String>) {
        _enabled.value = keys.toSet()
    }

    fun clear() {
        _enabled.value = emptySet()
    }
}
