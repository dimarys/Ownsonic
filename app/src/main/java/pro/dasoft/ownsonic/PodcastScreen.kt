package pro.dasoft.ownsonic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import pro.dasoft.ownsonic.data.Library
import pro.dasoft.ownsonic.data.LibraryStore
import pro.dasoft.ownsonic.data.PlaybackSource
import pro.dasoft.ownsonic.data.ServerSettings

@Composable
internal fun PodcastScreen(store: LibraryStore, library: Library, player: MediaController?, syncing: Boolean, tick: Int) {
    val actions = LocalLibraryActions.current
    val resources = LocalResources.current
    var error by remember { mutableStateOf<String?>(null) }
    val active = library.source == PlaybackSource.AEROSTAT
    val episode = library.episodes.find { active && it.audioId == player?.currentMediaItem?.mediaId }
        ?: library.episodes.find { it.id == library.podcastCurrentId }
    val ready = active && episode != null && player?.currentMediaItem?.mediaId == episode.audioId
    Text(stringResource(R.string.aerostat), fontSize = 26.sp, fontWeight = FontWeight.Bold)
    val podcastSettings by produceState(ServerSettings(), store) {
        val settings = store.onIo { settings(includePassword = false) }
        withContext(Dispatchers.Main.immediate) { value = settings }
    }
    Text(stringResource(if (podcastSettings.newestEpisodes) R.string.podcast_newest_description else R.string.aerostat_description, podcastSettings.episodeLimit), style = MaterialTheme.typography.bodySmall)
    if (episode != null) {
        val position = if (ready && tick >= 0) player?.currentPosition ?: library.podcastPosition else library.podcastPosition
        val duration = if (ready) player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: episode.duration else episode.duration
        val playing = ready && player?.isPlaying == true
        PlayerCover(store.audioFile(episode.audioId),
            artworkFile = episode.coverArtId.takeIf { it.isNotBlank() }?.let { store.coverFile(it) },
            cacheRevision = library.revision)
        Text(episode.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        PlaybackBar(position, duration, episode.audioId, ready) { fraction ->
            store.onIo { setFinished(false) }
            player?.seekTo((duration * fraction).toLong())
        }
        Text("${time(position)} / ${time(duration)}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaybackButton(R.drawable.ic_previous, stringResource(R.string.previous_episode), Modifier.weight(1f), ready) {
                store.onIo { setFinished(false) }
                if (player!!.currentMediaItemIndex > 0) player.seekTo(player.currentMediaItemIndex - 1, 0) else player.seekTo(0)
            }
            PlaybackButton(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) stringResource(R.string.pause) else stringResource(R.string.play),
                Modifier.weight(1f), !syncing && player != null && (!active || ready)) {
                if (playing) player?.pause()
                else if (!active) store.onIo { activatePodcast(episode.id) }
                else {
                    if (library.podcastFinished || player?.playbackState == Player.STATE_ENDED) player?.seekTo(0)
                    store.onIo { setFinished(false) }
                    player?.play()
                }
            }
            PlaybackButton(R.drawable.ic_stop, stringResource(R.string.stop_episode), Modifier.weight(1f), ready) { player?.stop() }
            PlaybackButton(R.drawable.ic_next, stringResource(R.string.next_episode), Modifier.weight(1f), ready) { next(player, duration, store) }
        }
        if (library.podcastFinished) Text(stringResource(R.string.podcast_finished), color = Accent)
        if (ready) player?.playerError?.let { Text(resources.getString(R.string.playback_error, it.errorCodeName), color = MaterialTheme.colorScheme.error) }
    } else Text(stringResource(R.string.aerostat_empty))
    library.episodes.forEach { item ->
        val heard = item.id in library.heardEpisodes
        val current = active && item.audioId == player?.currentMediaItem?.mediaId
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, resources.configuration.locales[0])
                    .format(java.util.Date(item.publishedAt)), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = !syncing && player != null, onClick = { actions.run {
                        if (current) {
                            if (player?.isPlaying == true) player.pause()
                            else {
                                if (library.podcastFinished) player?.seekTo(0)
                                store.onIo { setFinished(false) }
                                player?.play()
                            }
                        } else store.onIo { activatePodcast(item.id) }
                    } }, colors = ButtonDefaults.buttonColors(containerColor = Surface, contentColor = ControlColor),
                        modifier = Modifier.weight(1f).semantics {
                        contentDescription = resources.getString(R.string.play_episode, item.title)
                    }) {
                        Icon(painterResource(if (current && player?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                    FilterChip(selected = heard, enabled = !syncing, onClick = { actions.run {
                        runCatching { store.onIo { markEpisodeHeard(item.id, !heard) } }
                            .onFailure { error = resources.getString(R.string.heard_error) }
                    } }, label = { Text(stringResource(if (heard) R.string.heard else R.string.unheard)) },
                        modifier = Modifier.semantics {
                            contentDescription = resources.getString(if (heard) R.string.mark_unheard else R.string.mark_heard, item.title)
                        })
                }
            }
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
