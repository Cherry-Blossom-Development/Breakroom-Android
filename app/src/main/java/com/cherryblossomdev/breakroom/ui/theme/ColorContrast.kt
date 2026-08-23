package com.cherryblossomdev.breakroom.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Picks black or white — whichever gives higher contrast against this color per the WCAG
 * relative-luminance formula. Meant for arbitrary fixed accent colors used as a container
 * (e.g. `Button(containerColor = someAccentColor)`), where Material3's `ButtonDefaults`
 * content color defaults to a theme token (`onPrimary`) that has no relationship to the
 * actual container color passed in, and can end up illegible in one theme.
 *
 * A naive `luminance() > 0.5f` cutoff picks the wrong side for colors in the ~0.18-0.5
 * range (e.g. Material "Green 500" reads as black-on-green even though white also looks
 * plausible), so this compares the actual contrast ratios instead of guessing a cutoff.
 */
fun Color.contrastingContentColor(): Color {
    val contrastWithBlack = (luminance() + 0.05f) / 0.05f
    val contrastWithWhite = 1.05f / (luminance() + 0.05f)
    return if (contrastWithBlack >= contrastWithWhite) Color.Black else Color.White
}

/** WCAG contrast ratio between this color and [other], in the range [1, 21]. */
fun Color.contrastRatioWith(other: Color): Float {
    val l1 = luminance() + 0.05f
    val l2 = other.luminance() + 0.05f
    return if (l1 > l2) l1 / l2 else l2 / l1
}

/**
 * This color if it reads clearly against [background] (WCAG AA for small text, 4.5:1),
 * otherwise a black/white fallback chosen for guaranteed contrast against [background].
 *
 * For a fixed brand/accent color painted on a *theme-varying* background — e.g. Material
 * You's dynamic `primary`, which is derived from the user's wallpaper and isn't a fixed
 * value — a hardcoded text color can't be verified safe for every possible palette. This
 * keeps the brand color where it still works and only overrides it on the rare palette
 * where it wouldn't.
 */
fun Color.readableOn(background: Color): Color =
    if (contrastRatioWith(background) >= 4.5f) this else background.contrastingContentColor()

/**
 * Tracks the OS "Contrast" accessibility setting (Settings > Accessibility > Display size and
 * text > Contrast, API 34+) — Android's analog to iOS's `colorSchemeContrast` environment
 * value. [UiModeManager.getContrast] reports a level in [0, 1] (0 = Standard, ~0.5 = Medium,
 * 1 = High); anything above Standard is treated as "increased" here. Pre-34 devices have no
 * public API for this setting, so this always reports `false` on them.
 */
@Composable
fun isHighContrastEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false

    val context = LocalContext.current
    val uiModeManager = remember(context) {
        context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    }
    var contrast by remember(uiModeManager) { mutableFloatStateOf(uiModeManager.contrast) }

    DisposableEffect(context, uiModeManager) {
        val listener = UiModeManager.ContrastChangeListener { level -> contrast = level }
        uiModeManager.addContrastChangeListener(ContextCompat.getMainExecutor(context), listener)
        onDispose { uiModeManager.removeContrastChangeListener(listener) }
    }

    return contrast > 0f
}
