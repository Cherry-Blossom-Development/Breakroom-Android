package com.cherryblossomdev.breakroom.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.cherryblossomdev.breakroom.network.RetrofitClient

/**
 * Resolves a chat message's video_path to a playable URL, or null when the server
 * sent nothing usable (null, blank, or the literal string "null").
 */
fun chatVideoUrl(videoPath: String?): String? {
    val path = videoPath?.takeIf { it.isNotBlank() && it != "null" } ?: return null
    return if (path.startsWith("http")) path else "${RetrofitClient.BASE_URL}api/uploads/$path"
}

/**
 * Inline chat video: shows the video's first frame with a play overlay. Tapping it
 * opens [FullScreenVideoDialog], which fills the screen and plays with sound.
 * Replaces the old inline VideoView, which showed black until played, had a
 * poorly anchored MediaController inside lazy lists, and could fail on the
 * http -> https S3 redirect.
 */
@Composable
fun ChatVideoThumbnail(
    videoUrl: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    playIconSize: Dp = 48.dp,
    contentDescription: String = "Video, tap to play full screen"
) {
    var showFullScreen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Black)
            .clickable { showFullScreen = true }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = remember(videoUrl) {
                ImageRequest.Builder(context)
                    .data(videoUrl)
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .videoFrameMillis(0)
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .size(playIconSize)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(playIconSize * 0.7f)
            )
        }
    }

    if (showFullScreen) {
        FullScreenVideoDialog(videoUrl = videoUrl, onDismiss = { showFullScreen = false })
    }
}

/**
 * Full-screen video player. Auto-plays with audio (media/music stream, takes audio
 * focus), shows standard playback controls, and releases the player on close.
 */
@Composable
fun FullScreenVideoDialog(videoUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val context = LocalContext.current
        val player = remember(videoUrl) {
            // Allow cross-protocol redirects: /api/uploads/... 301s to S3 over https,
            // and the debug backend is plain http.
            val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                .build()
                .apply {
                    volume = 1f
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    prepare()
                    playWhenReady = true
                }
        }
        DisposableEffect(player) {
            onDispose { player.release() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close video", tint = Color.White)
            }
        }
    }
}
