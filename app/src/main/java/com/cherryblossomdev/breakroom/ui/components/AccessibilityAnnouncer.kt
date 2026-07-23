package com.cherryblossomdev.breakroom.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class AccessibilityAnnouncement(val id: Long = System.nanoTime(), val text: String)

/**
 * TalkBack equivalent of iOS's `AccessibilityNotification.Announcement(...).post()` --
 * Compose has no imperative "announce" call, so this renders an invisible live-region
 * node whose content triggers a TalkBack announcement when it appears. Each
 * [AccessibilityAnnouncement] carries a unique [AccessibilityAnnouncement.id] so `key()`
 * mounts a fresh node per call, guaranteeing an announcement even for repeated text.
 */
@Composable
fun AccessibilityAnnouncer(announcement: AccessibilityAnnouncement?) {
    if (announcement != null) {
        key(announcement.id) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = announcement.text
                    }
            )
        }
    }
}
