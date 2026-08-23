package com.cherryblossomdev.breakroom.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Tracks the OS "Remove animations" accessibility setting (Settings > Accessibility >
 * Remove animations) -- Android's analog to iOS's `accessibilityReduceMotion` environment
 * value. Backed by the same `Settings.Global.ANIMATOR_DURATION_SCALE` value the system uses
 * to disable animations globally: a scale of 0 means the user has motion reduced.
 */
@Composable
fun isReduceMotionEnabled(): Boolean {
    val context = LocalContext.current

    fun currentValue() = Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) == 0f

    var reduceMotion by remember(context) { mutableStateOf(currentValue()) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = currentValue()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    return reduceMotion
}
