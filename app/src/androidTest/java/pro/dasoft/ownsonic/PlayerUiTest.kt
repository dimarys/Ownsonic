package pro.dasoft.ownsonic

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pro.dasoft.ownsonic.data.LibraryStore
import pro.dasoft.ownsonic.data.Song
import pro.dasoft.ownsonic.data.PodcastEpisode
import pro.dasoft.ownsonic.data.PlaybackSource
import pro.dasoft.ownsonic.data.readEmbeddedCover
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class PlayerUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun syncedPodcastCoverDisplaysOfflineAndFallsBackWhenUnavailable() {
        val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
        FakeServer().use { server ->
            server.ids = emptyList()
            server.podcastCoverArt = "ui-podcast-cover"
            server.coverImages[server.podcastCoverArt] = podcastCoverPng(android.graphics.Color.GREEN)
            server.podcastEpisodes = listOf(org.json.JSONObject().put("id", "ui-covered-episode")
                .put("streamId", "ui-covered-stream").put("title", "Episode with server cover")
                .put("status", "completed").put("duration", 60).put("publishDate", "2020-01-01T00:00:00Z"))
            runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                pro.dasoft.ownsonic.data.SyncEngine(store, pro.dasoft.ownsonic.data.SubsonicClient(server.settings))
                    .run(server.settings.account) { _, _, _ -> }
            }
        }
        compose.onNodeWithText("Podcast").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Cover art").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("♫").assertDoesNotExist()
        val episode = store.library.value.episodes.single()
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            store.coverFile(episode.coverArtId).writeText("Invalid cached image")
            store.audioFile(episode.audioId).writeBytes(silentWav())
            store.replaceSyncedLibrary(store.library.value.account, emptyList(), listOf(episode))
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("♫").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Cover art").assertDoesNotExist()
    }

    @Test fun cancelButtonCancelsQueuedManualAndAutomaticSync() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = androidx.work.WorkManager.getInstance(context)
        manager.cancelUniqueWork(SyncWorker.NAME).result.get()
        compose.onNodeWithText("Sync", substring = false).performClick()
        for ((automatic, playbackTab) in listOf(false to "Music", false to "Podcast", true to "Podcast")) {
            compose.onNode(hasText(playbackTab) and hasClickAction()).performClick()
            val builder = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(1, java.util.concurrent.TimeUnit.HOURS)
            if (automatic) builder.addTag(AutoSyncScheduler.REQUEST_TAG)
            val request = builder.build()
            manager.enqueueUniqueWork(SyncWorker.NAME, androidx.work.ExistingWorkPolicy.REPLACE, request).result.get()
            val label = "✖️ ${compose.activity.getString(R.string.cancel_sync)}"
            if (!automatic) compose.waitUntil(5000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
            if (automatic) {
                compose.onNodeWithText("Music").assertIsEnabled()
                compose.onNode(hasText("Podcast") and hasClickAction()).assertIsEnabled()
                compose.onNode(hasText("Podcast") and hasClickAction()).assertIsSelected()
                compose.onNodeWithText("Sync", substring = false).performClick()
                compose.waitUntil(5000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
            } else {
                compose.onNodeWithText("Music").assertIsNotEnabled()
                compose.onNode(hasText("Podcast") and hasClickAction()).assertIsNotEnabled()
                compose.onNodeWithText("Sync", substring = false).assertIsSelected()
            }
            compose.onNodeWithText("Settings").assertIsEnabled()
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.waitUntil(5000) { manager.getWorkInfoById(request.id).get()?.state == androidx.work.WorkInfo.State.CANCELLED }
            compose.waitUntil(5000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText(compose.activity.getString(R.string.sync_interrupted)).assertExists()
            compose.onNodeWithText(compose.activity.getString(R.string.sync_button)).assertIsEnabled()
            compose.onNodeWithText("Music").assertIsEnabled()
            compose.onNode(hasText("Podcast") and hasClickAction()).assertIsEnabled()
        }
    }

    @Test fun podcastCompletionAndManualHeardMarksDoNotCreateMusicScrobbles() {
        val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
        val episodes = listOf(PodcastEpisode("natural-podcast-a", "stream-a", "First episode", 0, 2000),
            PodcastEpisode("natural-podcast-b", "stream-b", "Second episode", 86400000, 2000))
        episodes.forEach { store.audioFile(it.audioId).writeBytes(silentWav(2)) }
        compose.runOnIdle {
            store.replaceQueue("test-account", emptyList())
            store.replaceSyncedLibrary("test-account", emptyList(), episodes)
            episodes.forEach { store.markEpisodeHeard(it.id, false) }
        }
        val scrobbles = store.library.value.scrobbles.size
        compose.onNodeWithText("Podcast").performClick()
        clickControl("Play episode: First episode")
        compose.waitUntil(10000) { "natural-podcast-a" in store.library.value.heardEpisodes }
        compose.waitUntil(10000) { "natural-podcast-b" in store.library.value.heardEpisodes && store.library.value.podcastFinished }
        assertEquals(scrobbles, store.library.value.scrobbles.size)
        assertNull(store.listeningSession())
        clickControl("Mark not heard: First episode")
        compose.waitUntil(5000) { "natural-podcast-a" !in store.library.value.heardEpisodes }
        assertFalse("natural-podcast-a" in store.library.value.heardEpisodes)
        clickControl("Mark heard: First episode")
        compose.waitUntil(5000) { "natural-podcast-a" in store.library.value.heardEpisodes }
        assertTrue("natural-podcast-a" in store.library.value.heardEpisodes)
        assertEquals(store.library.value.heardEpisodes,
            LibraryStore(ApplicationProvider.getApplicationContext<Context>()).library.value.heardEpisodes)
    }

    @Test fun podcastAndMusicResumeSeparatelyAndSkipDoesNotMarkHeard() {
        val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
        val song = Song("podcast-switch-music", "Music bookmark", "Test artist", "Album", 60000)
        val episodes = listOf(PodcastEpisode("switch-a", "stream-a", "Bookmark episode A", 0, 60000),
            PodcastEpisode("switch-b", "stream-b", "Bookmark episode B", 86400000, 60000))
        store.audioFile(song.id).writeBytes(silentWav())
        episodes.forEach { store.audioFile(it.audioId).writeBytes(silentWav()) }
        compose.runOnIdle {
            store.replaceQueue("test-account", listOf(song))
            store.replaceSyncedLibrary("test-account", listOf(song), episodes)
            episodes.forEach { store.markEpisodeHeard(it.id, false) }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText(song.title).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10000) {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Playback position").performScrollTo().performTouchInput { click(Offset(width * 0.5f, center.y)) }
        compose.waitUntil(5000) { store.library.value.position in 29000..31000 }
        compose.onNodeWithText("Podcast").performClick()
        clickControl("Play episode: Bookmark episode A")
        compose.waitUntil(10000) { store.library.value.podcastPosition > 500 }
        val podcastPosition = store.library.value.podcastPosition
        compose.onNodeWithText("Music").performClick()
        clickControl("Play")
        compose.waitUntil(10000) { store.library.value.source == PlaybackSource.MUSIC && store.library.value.position > 30000 }
        assertTrue(store.library.value.podcastPosition >= podcastPosition)
        clickControl("Pause")
        compose.onNodeWithText("Podcast").performClick()
        clickControl("Play episode: Bookmark episode A")
        compose.waitUntil(10000) { store.library.value.source == PlaybackSource.AEROSTAT && store.library.value.podcastPosition > podcastPosition }
        clickControl("Stop and reset episode")
        compose.waitUntil(5000) { store.library.value.podcastPosition == 0L }
        clickControl("Next episode")
        compose.waitUntil(5000) { store.library.value.podcastCurrentId == "switch-b" }
        clickControl("Next episode")
        compose.waitUntil(5000) { store.library.value.podcastFinished }
        assertTrue(episodes.none { it.id in store.library.value.heardEpisodes })
    }

    @Test fun optionalReminderPlaysInBackgroundWithoutStoppingOrRepeating() {
        val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
        store.setRatingReminderEnabled(false)
        val song = Song("reminder", "Reminder test song", "Test artist", "Test album", 65000)
        store.audioFile(song.id).writeBytes(silentWav(65))
        store.replaceQueue("test-account", listOf(song))
        try {
            compose.onNodeWithText("Settings").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Rating reminder").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Rating reminder").performScrollTo().assertIsOff()
            clickControl("Rating reminder")
            compose.waitUntil(5000) { store.ratingReminderEnabled() }
            assertTrue(store.ratingReminderEnabled())
            compose.onNodeWithText("Music").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Play").fetchSemanticsNodes().isNotEmpty() }
            clickControl("Play")
            compose.waitUntil(10000) { store.library.value.position > 500 }
            compose.onNodeWithContentDescription("Playback position").performScrollTo().performTouchInput {
                click(Offset(width * (43f / 65f), center.y))
            }
            compose.waitUntil(5000) { store.library.value.position >= 42000 }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking {
                withTimeout(10000) { while (store.listeningSession()?.reminderPlayed != true) delay(100) }
                val before = store.library.value.position
                delay(1500)
                assertTrue(store.library.value.position > before)
            }
            val eventId = store.listeningSession()!!.eventId
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty() }
            clickControl("Pause")
            compose.onNodeWithContentDescription("Playback position").performScrollTo().performTouchInput {
                click(Offset(width * (46f / 65f), center.y))
            }
            assertEquals(eventId, store.listeningSession()!!.eventId)
            assertTrue(store.listeningSession()!!.reminderPlayed)
        } finally {
            store.setRatingReminderEnabled(false)
        }
    }

    @Test fun embeddedCoverAndFallbackFollowTheCurrentSong() {
        val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
        val covered = Song("covered", "Song with cover", "Test artist", "Test album", 1000)
        val plain = Song("plain", "Song without cover", "Test artist", "Test album", 60000)
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("embedded-cover.mp3").use { it.readBytes() }
        store.audioFile(covered.id).writeBytes(bytes)
        store.audioFile(plain.id).writeBytes(silentWav())
        val bitmap = readEmbeddedCover(store.audioFile(covered.id))!!
        assertTrue(bitmap.width <= 512 && bitmap.height <= 512)
        bitmap.recycle()
        assertNull(readEmbeddedCover(store.audioFile(plain.id)))
        assertNull(readEmbeddedCover(store.audioFile("missing")))
        store.audioFile("invalid").writeText("Invalid audio")
        assertNull(readEmbeddedCover(store.audioFile("invalid")))
        store.replaceQueue("test-account", listOf(covered, plain))
        fun assertCurrentCover(song: Song) {
            compose.waitUntil(10000) { compose.onAllNodesWithText(song.title).fetchSemanticsNodes().isNotEmpty() }
            if (song.id == covered.id) {
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Cover art").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("♫").assertDoesNotExist()
            } else {
                compose.onNodeWithText("♫").assertExists()
                compose.onNodeWithContentDescription("Cover art").assertDoesNotExist()
            }
        }
        val first = store.library.value.songs.first()
        assertCurrentCover(first)
        compose.waitUntil(10000) {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).fetchSemanticsNodes().isNotEmpty()
        }
        clickControl("Next song")
        assertCurrentCover(store.library.value.songs.last())
        clickControl("Previous song")
        assertCurrentCover(first)
    }

    @Test fun tappingPlaybackBarSeeksWithoutStartingPlayback() {
        val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
        val song = Song("seek-test", "Seek test song", "Test artist", "Test album", 60000)
        store.audioFile(song.id).writeBytes(silentWav())
        store.replaceQueue("test-account", listOf(song))
        compose.waitUntil(10000) { compose.onAllNodesWithText(song.title).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10000) {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Playback position").performScrollTo().performTouchInput {
            click(Offset(width * 0.75f, center.y))
        }
        compose.waitUntil(5000) { store.library.value.position in 44000L..46000L }
        compose.onNodeWithContentDescription("Play").assertExists()
        compose.onNodeWithContentDescription("Playback position").performTouchInput {
            click(Offset(width * 0.25f, center.y))
        }
        compose.waitUntil(5000) { store.library.value.position in 14000L..16000L }
        compose.onNodeWithContentDescription("Play").assertExists()
    }

    @Test fun naturalCompletionRecordsBothAutomaticTransitionAndFinalSong() {
        val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
        val songs = listOf(Song("natural-a", "Natural song A", "Test artist", "Test album", 31000),
            Song("natural-b", "Natural song B", "Test artist", "Test album", 31000))
        songs.forEach { store.audioFile(it.id).writeBytes(silentWav(31)) }
        val count = store.library.value.scrobbles.size
        store.replaceQueue("test-account", songs)
        val first = store.library.value.songs.first()
        compose.waitUntil(10000) { compose.onAllNodesWithText(first.title).fetchSemanticsNodes().isNotEmpty() }
        val started = System.currentTimeMillis()
        clickControl("Play")
        compose.waitUntil(45000) { store.library.value.scrobbles.size == count + 1 }
        val firstEvent = store.library.value.scrobbles.last()
        assertEquals(first.id, firstEvent.songId)
        assertTrue(firstEvent.startedAt in started..(started + 10000))
        compose.waitUntil(45000) { store.library.value.scrobbles.size == count + 2 && store.library.value.finished }
        assertNotEquals(firstEvent.eventId, store.library.value.scrobbles.last().eventId)
    }

    @Test fun playbackControlsStayAtSameHeightWithLongOrMissingMetadata() {
        val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
        val songs = listOf(
            Song("stable-short", "Short title", "Artist", "Album", 60000),
            Song("stable-long", "A very long song title ".repeat(12), "A very long artist name ".repeat(12), "", 60000),
            Song("stable-no-artist", "Another title", "", "A very long album title ".repeat(12), 60000),
        )
        songs.forEach { store.audioFile(it.id).writeBytes(silentWav()) }
        compose.runOnIdle { store.replaceQueue("test-account", songs) }
        val queue = store.library.value.songs
        var buttonTop: Float? = null
        queue.forEachIndexed { index, song ->
            compose.waitUntil(10000) {
                compose.onAllNodesWithText(song.title).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodes(hasContentDescription("Next song") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            val next = compose.onNodeWithContentDescription("Next song")
            next.performScrollTo()
            val top = next.fetchSemanticsNode().boundsInRoot.top
            buttonTop?.let { assertEquals("Metadata must not move playback controls", it, top, 1f) }
                ?: run { buttonTop = top }
            if (index < queue.lastIndex) next.performClick()
        }
    }

    @Test fun ratingNavigationStopAndEndOfQueue() {
        val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
        val count = store.library.value.scrobbles.size
        val ratings = store.ratingActions.value
        val songs = listOf(Song("ui-a", "Road song A", "Test artist", "Test album", 60000),
            Song("ui-b", "Road song B", "Test artist", "Test album", 60000))
        songs.forEach { store.audioFile(it.id).writeBytes(silentWav()) }
        store.replaceQueue("test-account", songs)
        val first = store.library.value.songs.first()
        compose.waitUntil(10000) { compose.onAllNodesWithText(first.title).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10000) {
            compose.onAllNodes(hasContentDescription(likeLabel(store)) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        clickControl(likeLabel(store))
        compose.waitUntil(3000) { store.library.value.pending[first.id] == ratings.like }
        assertEquals(first.id, store.library.value.currentId)
        clickControl(dislikeLabel(store))
        compose.waitUntil(5000) { store.library.value.currentId != first.id }
        assertEquals(ratings.dislike, store.library.value.pending[first.id])
        clickControl("Previous song")
        compose.waitUntil(5000) { store.library.value.currentId == first.id }
        clickControl(likeLabel(store))
        clickControl("Play")
        compose.waitUntil(10000) { store.library.value.position > 500 }
        clickControl("Stop and reset song")
        compose.waitUntil(5000) { store.library.value.position == 0L }
        assertEquals(ratings.like, store.library.value.pending[first.id])
        clickControl("Next song")
        compose.waitUntil(5000) { store.library.value.currentId != first.id }
        val last = store.library.value.currentId
        clickControl(dislikeLabel(store))
        compose.waitUntil(5000) { store.library.value.finished }
        assertEquals(last, store.library.value.currentId)
        assertEquals(count, store.library.value.scrobbles.size)
        compose.onNodeWithText("Queue finished. Use Previous to review your ratings.").assertExists()
        clickControl("Previous song")
        compose.waitUntil(5000) { store.library.value.currentId == first.id }
    }

    private fun clickControl(label: String) {
        compose.waitUntil(10000) {
            compose.onAllNodes(hasContentDescription(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
    }

    private fun likeLabel(store: LibraryStore) = compose.activity.getString(R.string.like, store.ratingActions.value.like)
    private fun dislikeLabel(store: LibraryStore) = compose.activity.getString(R.string.dislike, store.ratingActions.value.dislike)

    private fun silentWav(seconds: Int = 60): ByteArray {
        val dataSize = 8000 * 2 * seconds
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataSize)
        }.array()
    }
}
