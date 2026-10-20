package pro.dasoft.ownsonic

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import pro.dasoft.ownsonic.data.*
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.UUID
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class SyncIntegrationTest {
    @Before fun stopPlaybackBeforeWorkerTests() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        instrumentation.runOnMainSync { context.stopService(android.content.Intent(context, PlaybackService::class.java)) }
        instrumentation.waitForIdleSync()
    }

    @Test fun selectedSourceIdsAndEpisodeLimitDriveSyncAndSurviveRestart() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            server.playlistSources = listOf(ServerSource("first", "Road"), ServerSource("second", "Road"))
            server.playlistSongs = mapOf("first" to listOf("a"), "second" to listOf("b"))
            server.podcastChannelId = "42"
            server.podcastTitle = "Science daily"
            server.podcastEpisodes = (5 downTo 1).map { index ->
                JSONObject().put("id", "science-$index").put("streamId", "science-stream-$index")
                    .put("title", "Episode $index").put("status", "completed").put("duration", 60)
                    .put("publishDate", java.time.Instant.ofEpochMilli(index * 86400000L).toString())
            }
            val settings = server.settings.copy(playlistId = "second", playlistName = "Road", podcastId = "42",
                podcastName = "Science daily", episodeLimit = 2)
            val store = store()
            store.saveSettings(settings)
            store.replaceQueue(settings.account, listOf(song("a")))
            store.rate("a", 2)
            val client = SubsonicClient(settings)
            val catalog = client.catalog()
            assertEquals(2, catalog.playlists.size)
            assertEquals(listOf(ServerSource("42", "Science daily")), catalog.podcasts)
            SyncEngine(store, SubsonicClient(settings)).run(settings.account) { _, _, _ -> }
            assertEquals(2, server.ratings["a"])
            assertTrue(store.library.value.pending.isEmpty())
            assertEquals(listOf("second"), server.requestedPlaylists)
            assertEquals(listOf("42"), server.requestedPodcasts)
            assertEquals(listOf("b"), store.library.value.songs.map { it.id })
            assertEquals(listOf("science-1", "science-2"), store.library.value.episodes.map { it.id })
            assertEquals("Science daily", store.library.value.episodes.first().asSong().artist)
            assertEquals(settings, store.settings())
            // Deleting a selected playlist must preserve downloaded queues and existing files.
            server.playlistSources = emptyList()
            val before = store.library.value
            assertTrue(runCatching { SyncEngine(store, SubsonicClient(settings)).run(settings.account) { _, _, _ -> } }.isFailure)
            assertEquals(before, store.library.value)
            assertTrue(store.audioFile("b").exists())
        }
    }

    @Test fun customRatingsReachServerAndNewestSelectionStillPlaysChronologically() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val store = store()
            val settings = server.settings.copy(likeRating = 5, dislikeRating = 3, episodeLimit = 2, newestEpisodes = true)
            store.replaceQueue(settings.account, listOf(song("a"), song("b")))
            store.rate("a", settings.likeRating)
            store.rate("b", settings.dislikeRating)
            assertTrue(runCatching { store.rate("a", 0) }.isFailure)
            assertTrue(runCatching { store.rate("a", 6) }.isFailure)
            server.podcastEpisodes = listOf(2, 5, 1, 4, 3).map { index ->
                JSONObject().put("id", index.toString()).put("streamId", "stream-$index")
                    .put("title", "Episode $index").put("status", "completed").put("duration", 60)
                    .put("publishDate", java.time.Instant.ofEpochMilli(index * 86400000L).toString())
            } + JSONObject().put("id", "6").put("streamId", "stream-6").put("status", "new")
            SyncEngine(store, SubsonicClient(settings)).run(settings.account) { _, _, _ -> }
            assertEquals(mapOf("a" to 5, "b" to 3), server.ratings)
            assertTrue(store.library.value.pending.isEmpty())
            assertTrue(store.library.value.songs.isEmpty())
            assertEquals(listOf("4", "5"), store.library.value.episodes.map { it.id })
            assertEquals(listOf("stream-4", "stream-5"), server.downloads)
            store.markEpisodeHeard("5", true)
            SyncEngine(store, SubsonicClient(settings.copy(newestEpisodes = false))).run(settings.account) { _, _, _ -> }
            assertEquals(listOf("1", "2"), store.library.value.episodes.map { it.id })
            assertFalse(store.audioFile("podcast:4").exists())
            assertFalse(store.audioFile("podcast:5").exists())
            assertTrue("5" in store.library.value.heardEpisodes)
        }
    }

    @Test fun disablingPodcastsWorksWithoutPodcastApiAndRemovesCachedEpisodesOnlyAfterSuccess() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            server.failPodcasts = true
            val store = store()
            val old = PodcastEpisode("old", "old-stream", "Old episode", 0, 60000)
            store.replaceSyncedLibrary(server.settings.account, emptyList(), listOf(old))
            store.audioFile(old.audioId).writeText("Cached episode")
            val catalog = SubsonicClient(server.settings).catalog()
            assertEquals(1, catalog.playlists.size)
            assertNotNull(catalog.podcastError)
            server.methods.clear()
            val settings = server.settings.copy(podcastId = null)
            SyncEngine(store, SubsonicClient(settings)).run(settings.account) { _, _, _ -> }
            assertFalse(server.methods.contains("getPodcasts"))
            assertTrue(store.library.value.episodes.isEmpty())
            assertFalse(store.audioFile(old.audioId).exists())
            assertEquals(2, store.library.value.songs.size)
        }
    }

    @Test fun sourceSettingsMigrateAndPersistWithoutChangingTheCachedLibrary() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sources-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, name).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = directory
            override fun getSharedPreferences(key: String, mode: Int) = context.getSharedPreferences("$name.$key", mode)
        }
        val store = LibraryStore(isolated)
        assertNull(store.settings().podcastId)
        val original = ServerSettings("https://example.com", "tester", "secret")
        store.saveSettings(original)
        // Older installations have no source preferences.
        isolated.getSharedPreferences("connection", 0).edit().remove("playlistId").remove("playlistName")
            .remove("podcastId").remove("podcastName").remove("podcastDisabled").remove("episodeLimit")
            .remove("likeRating").remove("dislikeRating").remove("newestEpisodes").commit()
        assertEquals(original, LibraryStore(isolated).settings())
        store.replaceQueue(original.account, listOf(song("a")))
        store.rate("a", 1)
        val before = store.library.value
        val selected = original.copy(playlistId = "road", playlistName = "Road", podcastId = "42", podcastName = "Science", episodeLimit = 12, likeRating = 5, dislikeRating = 3, newestEpisodes = true)
        store.saveSettings(selected)
        assertEquals(selected, LibraryStore(isolated).settings())
        assertEquals(RatingActions(5, 3), LibraryStore(isolated).ratingActions.value)
        assertEquals(before, store.library.value)
        assertTrue(runCatching { store.saveSettings(selected.copy(episodeLimit = 0)) }.isFailure)
        assertTrue(runCatching { store.saveSettings(selected.copy(episodeLimit = 51)) }.isFailure)
        assertTrue(runCatching { store.saveSettings(selected.copy(likeRating = 0)) }.isFailure)
        assertTrue(runCatching { store.saveSettings(selected.copy(dislikeRating = 6)) }.isFailure)
        assertEquals(selected, LibraryStore(isolated).settings())
        store.saveSettings(selected.copy(podcastId = null))
        assertNull(LibraryStore(isolated).settings().podcastId)
    }

    @Test fun aerostatSelectsOldestFiveIncludesHeardAndRemovesDeletedEpisodes() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = File(context.cacheDir, "podcast-sync-${UUID.randomUUID()}").apply { mkdirs() }
            val isolated = object : ContextWrapper(context) {
                override fun getFilesDir() = directory
                override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("${directory.name}-$name", mode)
            }
            val store = LibraryStore(isolated)
            fun episode(index: Int) = JSONObject().put("id", index.toString()).put("streamId", "stream-$index")
                .put("title", "Episode $index").put("duration", 60).put("status", "completed")
                .put("publishDate", java.time.Instant.ofEpochMilli(index * 86400000L).toString())
            server.podcastEpisodes = (12 downTo 1).map(::episode) + episode(0).put("status", "new")
            val progress = mutableListOf<Pair<Int, Int>>()
            SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { message, completed, total ->
                if (message.code == MessageCode.DOWNLOADING) progress.add(completed to total)
            }
            assertEquals((1..5).map { it.toString() }, store.library.value.episodes.map { it.id })
            assertEquals(7, server.downloads.size)
            assertEquals(7 to 7, progress.last())
            assertEquals((1..5).map { "stream-$it" }, server.downloads.drop(2))
            assertNotEquals(store.audioFile("1"), store.audioFile("podcast:1"))
            store.markEpisodeHeard("1", true)
            store.activatePodcast("2")
            store.consumePlaybackRequest(store.library.value.revision)
            store.savePlayback("podcast:2", 12000, store.library.value.revision)
            // Simulate upgrading a cached ten-episode library to the five-episode limit.
            val legacyEpisodes = (1..10).map { PodcastEpisode(it.toString(), "stream-$it", "Episode $it", it * 86400000L, 60000) }
            store.replaceSyncedLibrary(server.settings.account, store.library.value.songs, legacyEpisodes)
            (6..10).forEach { store.audioFile("podcast:$it").writeText("Cached audio") }
            server.downloads.clear()
            SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> }
            assertTrue(server.downloads.isEmpty())
            assertEquals(5, store.library.value.episodes.size)
            (6..10).forEach { assertFalse(store.audioFile("podcast:$it").exists()) }
            assertEquals("1", store.library.value.episodes.first().id)
            assertTrue("1" in store.library.value.heardEpisodes)
            assertEquals(12000L, store.library.value.podcastPosition)
            assertFalse(store.library.value.playOnLoad)
            server.podcastEpisodes = (12 downTo 2).map(::episode)
            SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> }
            assertEquals((2..6).map { it.toString() }, store.library.value.episodes.map { it.id })
            assertFalse(store.audioFile("podcast:1").exists())
            assertEquals(listOf("stream-6"), server.downloads)
            assertTrue(store.audioFile("a").exists())
            assertEquals(store.library.value, LibraryStore(isolated).library.value)
            server.podcastEpisodes = emptyList()
            SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> }
            assertTrue(store.library.value.episodes.isEmpty())
            assertFalse(store.audioFile("podcast:2").exists())
        }
    }

    @Test fun failedPodcastDownloadPreservesBothQueuesAndExistingFiles() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val directory = File(context.cacheDir, "podcast-failure-${UUID.randomUUID()}").apply { mkdirs() }
            val isolated = object : ContextWrapper(context) {
                override fun getFilesDir() = directory
                override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("${directory.name}-$name", mode)
            }
            val store = LibraryStore(isolated)
            val old = PodcastEpisode("old", "old-stream", "Old episode", 0, 60000)
            store.replaceSyncedLibrary(server.settings.account, listOf(song("old-song")), listOf(old))
            store.audioFile(old.audioId).writeText("Old audio")
            store.audioFile("old-song").writeText("Old music")
            store.audioFile("a").writeText("Already downloaded")
            store.audioFile("b").writeText("Already downloaded")
            store.markEpisodeHeard(old.id, true)
            val before = store.library.value
            server.podcastEpisodes = listOf(JSONObject().put("id", "new").put("streamId", "new-stream")
                .put("status", "completed").put("title", "New episode").put("duration", 60)
                .put("publishDate", "2020-01-01T00:00:00Z"))
            server.failDownload = true
            assertTrue(runCatching { SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> } }.isFailure)
            assertEquals(before, store.library.value)
            assertTrue(store.audioFile(old.audioId).exists())
            assertTrue(store.audioFile("old-song").exists())
            assertFalse(store.audioFile("podcast:new").exists())
        }
    }

    @Test fun separatePodcastAndMusicBookmarksAndHeardMarksSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "podcast-bookmarks-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = directory
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("${directory.name}-$name", mode)
        }
        val store = LibraryStore(isolated)
        val episode = PodcastEpisode("7", "700", "Episode", 0, 60000)
        store.replaceSyncedLibrary("test-account", listOf(song("7")), listOf(episode))
        store.savePlayback("7", 17000, store.library.value.revision)
        store.activatePodcast(episode.id)
        assertTrue(store.consumePlaybackRequest(store.library.value.revision))
        assertFalse(store.consumePlaybackRequest(store.library.value.revision))
        store.savePlayback(episode.audioId, 23000, store.library.value.revision)
        store.markEpisodeHeard(episode.id, true)
        val restored = LibraryStore(isolated)
        assertEquals(store.library.value, restored.library.value)
        assertEquals(17000L, restored.library.value.position)
        assertEquals(23000L, restored.library.value.podcastPosition)
        assertEquals(PlaybackSource.AEROSTAT, restored.library.value.source)
        restored.activateMusic(false)
        assertEquals(23000L, restored.library.value.podcastPosition)
        assertEquals(17000L, restored.library.value.position)
        restored.markEpisodeHeard(episode.id, false)
        assertFalse(episode.id in LibraryStore(isolated).library.value.heardEpisodes)
    }

    @Test fun automaticSyncHonorsDisabledEveningAndMorningRules() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = LibraryStore.get(context)
            val manager = WorkManager.getInstance(context)
            store.library.value.pending.forEach { (id, rating) -> store.confirmRating(id, rating) }
            store.library.value.scrobbles.forEach { store.confirmScrobble(it.eventId) }
            store.replaceQueue(server.settings.account, emptyList())
            store.saveSettings(server.settings)
            suspend fun run(slot: AutoSyncSlot): WorkInfo {
                val request = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setInputData(workDataOf(AutoSyncScheduler.SLOT to slot.name)).build()
                manager.enqueue(request).result.get()
                return withTimeout(15000) {
                    var info = manager.getWorkInfoById(request.id).get()!!
                    while (!info.state.isFinished) { delay(100); info = manager.getWorkInfoById(request.id).get()!! }
                    info
                }
            }
            try {
                store.setAutomaticSyncEnabled(false)
                assertEquals(WorkInfo.State.SUCCEEDED, run(AutoSyncSlot.MORNING).state)
                assertTrue(server.methods.isEmpty())
                store.setAutomaticSyncEnabled(true)
                assertEquals(WorkInfo.State.SUCCEEDED, run(AutoSyncSlot.EVENING).state)
                assertTrue(server.methods.isEmpty())
                assertNotNull(AutoSyncScheduler.wifiNetwork(context))
                assertEquals(WorkInfo.State.SUCCEEDED, run(AutoSyncSlot.MORNING).state)
                assertTrue(server.methods.contains("getPlaylist"))
                server.methods.clear()
                store.rate("a", 2)
                store.enqueueScrobble(Scrobble("automatic-listen-${UUID.randomUUID()}", "a", 123_000))
                assertEquals(WorkInfo.State.SUCCEEDED, run(AutoSyncSlot.EVENING).state)
                assertEquals(2, server.ratings["a"])
                assertEquals(1, server.listens.size)
                assertFalse(store.hasPendingChanges())
            } finally {
                store.setAutomaticSyncEnabled(false)
                AutoSyncScheduler.schedule(context)
            }
        }
    }

    @Test fun automaticSchedulesCanBeEnabledRescheduledAndCancelled() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LibraryStore.get(context)
        val manager = WorkManager.getInstance(context)
        try {
            store.setAutomaticSyncEnabled(true)
            AutoSyncScheduler.schedule(context, refresh = true)
            withTimeout(10000) {
                while (manager.getWorkInfosByTag(AutoSyncScheduler.SCHEDULE_TAG).get().count { !it.state.isFinished } != 2) delay(100)
            }
            AutoSyncSlot.entries.forEach { slot ->
                val info = manager.getWorkInfosForUniqueWork(AutoSyncScheduler.name(slot)).get().single { !it.state.isFinished }
                assertTrue(info.nextScheduleTimeMillis > System.currentTimeMillis())
            }
            AutoSyncScheduler.schedule(context)
            assertEquals(2, manager.getWorkInfosByTag(AutoSyncScheduler.SCHEDULE_TAG).get().count { !it.state.isFinished })
            store.library.value.pending.forEach { (id, rating) -> store.confirmRating(id, rating) }
            store.library.value.scrobbles.forEach { store.confirmScrobble(it.eventId) }
            val immediate = PeriodicWorkRequestBuilder<AutoSyncWorker>(1, TimeUnit.DAYS)
                .setInputData(workDataOf(AutoSyncScheduler.SLOT to AutoSyncSlot.EVENING.name))
                .setNextScheduleTimeOverride(System.currentTimeMillis()).addTag(AutoSyncScheduler.SCHEDULE_TAG).build()
            manager.cancelUniqueWork(AutoSyncScheduler.name(AutoSyncSlot.EVENING)).result.get()
            manager.enqueueUniquePeriodicWork(AutoSyncScheduler.name(AutoSyncSlot.EVENING), ExistingPeriodicWorkPolicy.KEEP, immediate).result.get()
            withTimeout(10000) {
                while (manager.getWorkInfoById(immediate.id).get()!!.nextScheduleTimeMillis <= System.currentTimeMillis() + 60000) delay(100)
            }
        } finally {
            store.setAutomaticSyncEnabled(false)
            AutoSyncScheduler.schedule(context)
            withTimeout(10000) {
                while (manager.getWorkInfosByTag(AutoSyncScheduler.SCHEDULE_TAG).get().any { !it.state.isFinished }) delay(100)
            }
        }
    }

    @Test fun downloadProgressCountsOnlyMissingUnratedFilesAndCompletesEachSong() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            server.ids = listOf("cached", "rated", "a", "b")
            server.ratings["rated"] = 2
            val store = store()
            store.audioFile("cached").writeBytes(byteArrayOf(1))
            store.audioFile("a").writeBytes(byteArrayOf())
            val progress = mutableListOf<Pair<Int, Int>>()
            assertEquals(3, SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { message, completed, total ->
                if (message.code == MessageCode.DOWNLOADING) progress.add(completed to total)
            })
            assertEquals(listOf("a", "b"), server.downloads)
            assertEquals(listOf(0 to 2, 1 to 2, 1 to 2, 2 to 2), progress)
        }
    }

    @Test fun cachedLibraryHasNoDownloadProgressOrDownloads() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val store = store()
            server.ids.forEach { store.audioFile(it).writeBytes(byteArrayOf(1)) }
            val messages = mutableListOf<AppMessage>()
            assertEquals(2, SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { message, _, _ -> messages.add(message) })
            assertTrue(server.downloads.isEmpty())
            assertFalse(messages.any { it.code == MessageCode.DOWNLOADING })
            assertTrue(messages.any { it.code == MessageCode.NO_DOWNLOADS })
        }
    }

    @Test fun scrobblesUploadInTimeOrderBeforePlaylistRefreshAndDoNotRepeat() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val store = store()
            val later = Scrobble("later", "a", 200_000)
            val earlier = Scrobble("earlier", "a", 100_000)
            store.enqueueScrobble(later)
            store.enqueueScrobble(earlier)
            SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> }
            assertEquals(listOf(100_000L, 200_000L), server.listens.map { it.second })
            assertTrue(server.listens.all { it.first == "a" && it.third })
            assertTrue(store.library.value.scrobbles.isEmpty())
            assertEquals(listOf("scrobble", "scrobble", "getPlaylists"), server.methods.take(3))
            store.enqueueScrobble(earlier)
            assertTrue(store.library.value.scrobbles.isEmpty())
            SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> }
            assertEquals(2, server.listens.size)
        }
    }

    @Test fun failedScrobbleRemainsPendingAndPreservesPlayback() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            server.failScrobble = true
            val store = store()
            store.replaceQueue(server.settings.account, listOf(song("a")))
            store.savePlayback("a", 12345, store.library.value.revision)
            store.enqueueScrobble(Scrobble("event", "a", 100_000))
            val before = store.library.value
            assertTrue(runCatching { SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> } }.isFailure)
            assertEquals(before, store.library.value)
            assertEquals(listOf("scrobble"), server.methods)
        }
    }

    @Test fun cancellingStalledDownloadPreservesLibraryAndAllowsRetry() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = LibraryStore.get(context)
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(SyncWorker.NAME).result.get()
            store.replaceQueue(server.settings.account, listOf(song("removed")))
            store.saveSettings(server.settings)
            store.audioFile("removed").writeBytes(byteArrayOf(1))
            store.audioFile("a").delete()
            store.audioFile("b").delete()
            store.savePlayback("removed", 7000, store.library.value.revision)
            val before = store.library.value
            server.stalledDownload = "b"
            val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
            manager.enqueue(request).result.get()
            assertTrue(server.downloadStalled.await(10, TimeUnit.SECONDS))
            val partial = File(store.audioFile("b").path + ".part")
            withTimeout(5000) { while (!partial.exists()) delay(20) }
            manager.cancelWorkById(request.id).result.get()
            withTimeout(5000) { while (partial.exists()) delay(20) }
            assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(request.id).get()!!.state)
            assertEquals(before, store.library.value)
            assertTrue(store.audioFile("removed").exists())
            assertTrue(store.audioFile("a").exists())
            assertFalse(store.audioFile("b").exists())
            server.stalledDownload = null
            server.releaseDownload.countDown()
            val retry = OneTimeWorkRequestBuilder<SyncWorker>().build()
            manager.enqueue(retry).result.get()
            withTimeout(10000) { while (manager.getWorkInfoById(retry.id).get()?.state?.isFinished != true) delay(50) }
            assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(retry.id).get()!!.state)
            assertEquals(listOf("a", "b").toSet(), store.library.value.songs.map { it.id }.toSet())
            assertEquals(listOf("a", "b", "b"), server.downloads)
            assertFalse(store.audioFile("removed").exists())
        }
    }

    @Test fun foregroundWorkerCompletesManualSync() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = LibraryStore.get(context)
            store.replaceQueue(server.settings.account, emptyList())
            store.saveSettings(server.settings)
            val manager = WorkManager.getInstance(context)
            val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
            manager.enqueue(request).result.get()
            val info = withTimeout(15000) {
                var latest = requireNotNull(manager.getWorkInfoById(request.id).get())
                while (!latest.state.isFinished) {
                    delay(100)
                    latest = requireNotNull(manager.getWorkInfoById(request.id).get())
                }
                latest
            }
            assertEquals(info.outputData.getString("message"), WorkInfo.State.SUCCEEDED, info.state)
            assertEquals(2, store.library.value.songs.size)
        }
    }

    private fun store(): LibraryStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = UUID.randomUUID().toString()
        val directory = File(context.cacheDir, name).apply { mkdirs() }
        return LibraryStore(object : ContextWrapper(context) {
            override fun getFilesDir() = directory
            override fun getSharedPreferences(key: String, mode: Int) = context.getSharedPreferences("$name.$key", mode)
        })
    }

    @Test fun successfulSyncUploadsRatingsDownloadsRemovesAndResets() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val store = store()
            store.replaceQueue(server.settings.account, listOf(song("removed"), song("a")))
            store.audioFile("removed").writeBytes(byteArrayOf(1))
            store.audioFile("a").writeBytes(byteArrayOf(1))
            store.rate("a", 2)
            store.savePlayback("a", 5000, store.library.value.revision)
            val previousRevision = store.library.value.revision
            val count = SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> }
            assertEquals(1, count)
            assertEquals(2, server.ratings["a"])
            assertTrue(store.library.value.pending.isEmpty())
            assertEquals(setOf("b"), store.library.value.songs.map { it.id }.toSet())
            assertEquals(previousRevision + 1, store.library.value.revision)
            assertEquals(0L, store.library.value.position)
            assertFalse(store.audioFile("removed").exists())
            assertTrue(store.audioFile("b").length() > 0)
            assertFalse(store.audioFile("a").exists())
            assertEquals(listOf("b"), server.downloads)
            assertTrue(server.authValid)
            assertEquals(listOf("setRating", "getPlaylists", "getPlaylist", "getPodcasts", "download"), server.methods)
        }
    }

    @Test fun serverRatedSongsAreExcludedFromDownloadsAndRemovedLocally() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            server.ids = listOf("a", "b", "c")
            server.ratings["a"] = 1
            server.ratings["c"] = 5
            val store = store()
            store.replaceQueue(server.settings.account, listOf(song("a")))
            store.audioFile("a").writeBytes(byteArrayOf(1))
            assertEquals(1, SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> })
            assertEquals(listOf("b"), server.downloads)
            assertEquals(listOf("b"), store.library.value.songs.map { it.id })
            assertFalse(store.audioFile("a").exists())
            assertFalse(store.audioFile("c").exists())
            assertEquals(listOf("a", "b", "c"), server.ids)
        }
    }

    @Test fun allRatedSongsProduceAnEmptyQueueWithoutDownloads() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            server.ratings["a"] = 2
            server.ratings["b"] = 1
            val store = store()
            store.replaceQueue(server.settings.account, listOf(song("a")))
            store.audioFile("a").writeBytes(byteArrayOf(1))
            assertEquals(0, SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> })
            assertTrue(server.downloads.isEmpty())
            assertTrue(store.library.value.songs.isEmpty())
            assertEquals("", store.library.value.currentId)
            assertFalse(store.audioFile("a").exists())
        }
    }

    @Test fun uploadedLocalRatingExcludesSongEvenWhenPlaylistRatingIsStale() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            server.reportRatings = false
            val store = store()
            store.replaceQueue(server.settings.account, listOf(song("a")))
            store.rate("a", 2)
            assertEquals(1, SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> })
            assertEquals(2, server.ratings["a"])
            assertEquals(listOf("b"), server.downloads)
            assertEquals(listOf("b"), store.library.value.songs.map { it.id })
            assertTrue(store.library.value.pending.isEmpty())
        }
    }

    @Test fun failedDownloadPreservesQueuePositionAndOldFiles() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            server.failDownload = true
            server.ratings["a"] = 2
            val store = store()
            store.replaceQueue(server.settings.account, listOf(song("removed"), song("a")))
            store.audioFile("removed").writeBytes(byteArrayOf(1))
            store.audioFile("a").writeBytes(byteArrayOf(1))
            store.savePlayback("removed", 7000, store.library.value.revision)
            val before = store.library.value
            val progress = mutableListOf<Pair<Int, Int>>()
            val result = runCatching { SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { message, completed, total ->
                if (message.code == MessageCode.DOWNLOADING) progress.add(completed to total)
            } }
            assertTrue(result.isFailure)
            assertEquals(before, store.library.value)
            assertTrue(store.audioFile("removed").exists())
            assertTrue(store.audioFile("a").exists())
            assertEquals(listOf("b"), server.downloads)
            assertEquals(listOf(0 to 1), progress)
        }
    }

    @Test fun failedRatingUploadKeepsLatestLocalRating() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            server.failRating = true
            val store = store()
            store.replaceQueue(server.settings.account, listOf(song("a")))
            store.rate("a", 1)
            store.rate("a", 2)
            assertTrue(runCatching { SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> } }.isFailure)
            assertEquals(2, store.library.value.pending["a"])
            assertEquals(listOf("setRating"), server.methods)
            store.rate("a", 1)
            store.confirmRating("a", 2)
            assertEquals(1, store.library.value.pending["a"])
        }
    }

    @Test fun emptyPlaylistSuccessfullyClearsLocalMusic() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            server.ids = emptyList()
            val store = store()
            store.replaceQueue(server.settings.account, listOf(song("a")))
            store.audioFile("a").writeBytes(byteArrayOf(1))
            assertEquals(0, SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> })
            assertTrue(store.library.value.songs.isEmpty())
            assertFalse(store.audioFile("a").exists())
        }
    }

    @Test fun persistedLibraryRestoresOrderRatingsPositionAndEncryptedPassword() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = UUID.randomUUID().toString()
        val directory = File(context.cacheDir, name).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = directory
            override fun getSharedPreferences(key: String, mode: Int) = context.getSharedPreferences("$name.$key", mode)
        }
        val store = LibraryStore(isolated)
        assertFalse(store.ratingReminderEnabled())
        assertFalse(store.automaticSyncEnabled())
        store.setAutomaticSyncEnabled(true)
        store.setRatingReminderEnabled(true)
        store.saveListeningSession(ListeningSession(songId = "b", revision = 1, startedAt = 123_000, reminderPlayed = true))
        val settings = ServerSettings("https://example.com/airsonic", "user", "test-secret")
        store.saveSettings(settings)
        store.replaceQueue(settings.account, listOf(song("a"), song("b"), song("c")))
        store.rate("b", 2)
        store.enqueueScrobble(Scrobble("persisted-event", "b", 123_000))
        store.savePlayback("b", 12345, store.library.value.revision)
        val restored = LibraryStore(isolated)
        assertTrue(restored.ratingReminderEnabled())
        assertTrue(restored.automaticSyncEnabled())
        assertEquals(store.listeningSession(), restored.listeningSession())
        assertEquals(store.library.value, restored.library.value)
        assertEquals(settings, restored.settings())
        assertFalse(isolated.getSharedPreferences("connection", 0).getString("password", "")!!.contains("test-secret"))
        store.replaceQueue(settings.account, listOf(song("a")))
        store.savePlayback("b", 99999, restored.library.value.revision)
        assertEquals("a", store.library.value.currentId)
        assertEquals(0L, store.library.value.position)
    }

    private fun song(id: String) = Song(id, "Song $id", "Test artist", "Test album", 60000)
}

internal class FakeServer : AutoCloseable {
    private val socket = ServerSocket(0)
    val settings = ServerSettings("http://127.0.0.1:${socket.localPort}", "tester", "secret")
    val ratings = mutableMapOf<String, Int>()
    val methods = mutableListOf<String>()
    val downloads = mutableListOf<String>()
    var ids = listOf("a", "b")
    var playlistSources = listOf(ServerSource("list", "laterlist"))
    var playlistSongs: Map<String, List<String>> = emptyMap()
    var podcastChannelId = "7"
    var podcastTitle = "Aerostat"
    var podcastCoverArt = ""
    val coverImages = mutableMapOf<String, ByteArray>()
    val requestedCovers = mutableListOf<String>()
    @Volatile var stalledCover: String? = null
    var failPodcasts = false
    val requestedPlaylists = mutableListOf<String>()
    val requestedPodcasts = mutableListOf<String>()
    var podcastEpisodes: List<JSONObject> = emptyList()
    @Volatile var stalledDownload: String? = null
    val downloadStalled = CountDownLatch(1)
    val releaseDownload = CountDownLatch(1)
    var failDownload = false
    var failRating = false
    var reportRatings = true
    var authValid = true
    var failScrobble = false
    val listens = mutableListOf<Triple<String, Long, Boolean>>()
    private val worker = thread(isDaemon = true) {
        while (!socket.isClosed) {
            try {
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    val line = reader.readLine()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val uri = Uri.parse("http://localhost" + line.split(' ')[1])
                    val method = uri.lastPathSegment!!.removeSuffix(".view")
                    methods.add(method)
                    if (method == "download") downloads.add(uri.getQueryParameter("id")!!)
                    if (method == "getCoverArt") requestedCovers.add(uri.getQueryParameter("id")!!)
                    if (method == "getPlaylist") requestedPlaylists.add(uri.getQueryParameter("id")!!)
                    if (method == "getPodcasts") uri.getQueryParameter("id")?.let { requestedPodcasts.add(it) }
                    val expected = MessageDigest.getInstance("MD5").digest((settings.password + uri.getQueryParameter("s")).toByteArray()).joinToString("") { "%02x".format(it) }
                    authValid = authValid && uri.getQueryParameter("t") == expected &&
                        uri.getQueryParameter("p") == null && uri.getQueryParameter("v") == "1.13.0"
                    val payload = when (method) {
                        "scrobble" -> {
                            if (failScrobble) JSONObject().put("status", "failed").put("error", JSONObject().put("code", 50))
                            else {
                                listens.add(Triple(uri.getQueryParameter("id")!!, uri.getQueryParameter("time")!!.toLong(), uri.getQueryParameter("submission") == "true"))
                                JSONObject().put("status", "ok")
                            }
                        }
                        "setRating" -> {
                            if (failRating) JSONObject().put("status", "failed").put("error", JSONObject().put("code", 50))
                            else { ratings[uri.getQueryParameter("id")!!] = uri.getQueryParameter("rating")!!.toInt(); JSONObject().put("status", "ok") }
                        }
                        "getPlaylists" -> JSONObject().put("status", "ok").put("playlists", JSONObject().put("playlist",
                            JSONArray(playlistSources.map { JSONObject().put("id", it.id).put("name", it.name) })))
                        "getPodcasts" -> if (failPodcasts) JSONObject().put("status", "failed").put("error", JSONObject().put("code", 0))
                            else JSONObject().put("status", "ok").put("podcasts", JSONObject().put("channel",
                                JSONArray().put(JSONObject().put("id", podcastChannelId).put("title", podcastTitle)
                                    .put("coverArt", podcastCoverArt).put("episode", JSONArray(podcastEpisodes)))))
                        "getPlaylist" -> JSONObject().put("status", "ok").put("playlist", JSONObject().put("entry", JSONArray((playlistSongs[uri.getQueryParameter("id")] ?: ids).map { JSONObject().put("id", it).put("title", "Song $it").put("duration", 60).put("userRating", if (reportRatings) ratings[it] ?: 0 else 0) })))
                        else -> JSONObject()
                    }
                    if ((method == "download" && uri.getQueryParameter("id") == stalledDownload) ||
                        (method == "getCoverArt" && uri.getQueryParameter("id") == stalledCover)) {
                        client.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\nConnection: close\r\n\r\n".toByteArray())
                            write(ByteArray(8192) { 65 }); flush()
                        }
                        downloadStalled.countDown()
                        releaseDownload.await(20, TimeUnit.SECONDS)
                        return@use
                    }
                    val image = if (method == "getCoverArt") coverImages[uri.getQueryParameter("id")] else null
                    val failed = (method == "download" && failDownload) || (method == "getCoverArt" && image == null)
                    val bytes = image ?: if (method == "download") "RIFFtestaudio".toByteArray() else JSONObject().put("subsonic-response", payload).toString().toByteArray()
                    client.getOutputStream().apply {
                        write("HTTP/1.1 ${if (failed) "500 Error" else "200 OK"}\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                }
            } catch (_: Exception) { }
        }
    }
    override fun close() { releaseDownload.countDown(); socket.close(); worker.join(1000) }
}
