package com.cherryblossomdev.breakroom.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * Converts a fixed size (expressed as if it were an sp value) into a [Dp] that scales
 * with the user's system font size setting, the same way text does.
 *
 * Plain [Dp] sizes (e.g. `Modifier.size(40.dp)`) never scale with the accessibility
 * "Larger Text" font size setting, so avatars/icons stay visually undersized next to
 * text that has grown. Passing the same numeric value through here keeps them
 * proportionate — mirroring iOS's `@ScaledMetric`.
 */
@Composable
fun scaledDp(baseSp: Int): Dp = with(LocalDensity.current) { baseSp.sp.toDp() }

/**
 * True once the user's system font size is scaled up enough that single-line/truncated
 * layouts start clipping real content, roughly the point iOS's accessibility Dynamic
 * Type sizes (AX1+) kick in. Android's font scale is continuous rather than discrete
 * steps, so this is a threshold rather than an exact equivalent — used to relax
 * `maxLines` limits and switch cramped rows to a stacked layout.
 */
@Composable
fun isLargeTextScale(): Boolean = LocalConfiguration.current.fontScale >= 1.3f
