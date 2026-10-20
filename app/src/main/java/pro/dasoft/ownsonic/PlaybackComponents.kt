package pro.dasoft.ownsonic

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pro.dasoft.ownsonic.data.LibraryStore
import pro.dasoft.ownsonic.data.readEmbeddedCover
import pro.dasoft.ownsonic.data.readCover

internal const val MetadataMarqueePauseMillis = 5_000

internal val Accent = Color(0xFFB8F36E)
internal val Surface = Color(0xFF1D2520)
internal val ControlColor = Color(0xFFD2D8D3)

@Composable
internal fun PlaybackBar(position: Long, duration: Long, itemId: String, enabled: Boolean, onSeek: suspend (Float) -> Unit) {
    val actions = LocalLibraryActions.current
    val direction = LocalLayoutDirection.current
    val label = stringResource(R.string.playback_position)
    val seek: (Float) -> Boolean = { fraction ->
        if (enabled && duration > 0) { actions.run { onSeek(fraction.coerceIn(0f, 1f)) }; true } else false
    }
    Box(Modifier.fillMaxWidth().height(32.dp)
        .semantics(mergeDescendants = true) {
            contentDescription = label
            if (enabled && duration > 0) setProgress { seek(it) }
        }
        .pointerInput(itemId, duration, enabled, direction) {
            detectTapGestures { offset ->
                val fraction = offset.x / size.width.coerceAtLeast(1)
                seek(if (direction == LayoutDirection.Rtl) 1f - fraction else fraction)
            }
        }, contentAlignment = Alignment.Center) {
        LinearProgressIndicator(progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
    }
}

@Composable
internal fun PlayerCover(file: File?, height: Dp = 140.dp, artworkFile: File? = null, cacheRevision: Long = 0) {
    key(file?.absolutePath, artworkFile?.absolutePath, cacheRevision) {
        val cover by produceState<ImageBitmap?>(null) {
            val bitmap = withContext(Dispatchers.IO) {
                (artworkFile?.let { readCover(it) } ?: file?.let { readEmbeddedCover(it) })?.asImageBitmap()
            }
            withContext(Dispatchers.Main.immediate) { value = bitmap }
        }
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp)).background(Surface),
            contentAlignment = Alignment.Center) {
            if (cover != null) {
                Image(bitmap = cover!!, contentDescription = stringResource(R.string.cover_art), modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit)
            } else {
                Text("♫", fontSize = 100.sp, color = Accent)
            }
        }
    }
}

@Composable
internal fun PlaybackButton(icon: Int, label: String, modifier: Modifier, enabled: Boolean, selected: Boolean = false, action: suspend () -> Unit) {
    val actions = LocalLibraryActions.current
    Button(onClick = { actions.run(action) }, enabled = enabled && !actions.busy, modifier = modifier.height(64.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color(0xFF2D3928) else Surface,
            contentColor = if (selected) Accent else ControlColor)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
    }
}

internal suspend fun next(player: MediaController?, duration: Long, store: LibraryStore) {
    if (player == null) return
    if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    else { player.pause(); player.seekTo(duration.coerceAtLeast(0)); store.onIo { setFinished(true) } }
}

internal fun time(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
