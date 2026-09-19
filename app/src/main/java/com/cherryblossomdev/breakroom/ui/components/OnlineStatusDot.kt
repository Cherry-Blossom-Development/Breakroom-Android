package com.cherryblossomdev.breakroom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cherryblossomdev.breakroom.PresenceStore

private val OnlineColor = Color(0xFF4CAF50)

// A small dot showing whether userId currently has an active socket connection.
// Reads PresenceStore directly (Compose snapshot state) so callers don't need to thread
// presence through their own state -- mirrors FeaturesStore.has() usage elsewhere.
@Composable
fun OnlineStatusDot(
    userId: Int,
    showLabel: Boolean = false,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val online = PresenceStore.isOnline(userId)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = if (online) "Online" else "Offline"
        }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (online) OnlineColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        if (showLabel && online) {
            Text(
                text = "Online now",
                style = MaterialTheme.typography.labelSmall,
                color = OnlineColor
            )
        }
    }
}
