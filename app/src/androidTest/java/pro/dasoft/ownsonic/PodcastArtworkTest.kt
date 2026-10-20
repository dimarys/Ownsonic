package pro.dasoft.ownsonic

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pro.dasoft.ownsonic.data.*

@RunWith(AndroidJUnit4::class)
class PodcastArtworkTest {
    @Test fun sharedAndEpisodeCoversDownloadOnceAndRestoreOffline() = runBlocking(Dispatchers.IO) {
        val context = isolatedContext()
        val store = LibraryStore(context)
        FakeServer().use { server ->
            server.ids = emptyList()
            server.podcastCoverArt = "pod-7/../image"
            server.coverImages[server.podcastCoverArt] = podcastCoverPng(Color.GREEN)
            server.coverImages["episode-cover"] = podcastCoverPng(Color.BLUE)
            server.podcastEpisodes = listOf(episode("1"), episode("2"), episode("3").put("coverArt", "episode-cover"))
            sync(store, server)
            assertEquals(listOf(server.podcastCoverArt, "episode-cover"), server.requestedCovers)
            assertTrue(server.authValid)
            assertEquals(listOf(server.podcastCoverArt, server.podcastCoverArt, "episode-cover"), store.library.value.episodes.map { it.coverArtId })
            assertEquals(2, store.coverDirectory.listFiles()!!.size)
            assertEquals(store.coverDirectory, store.coverFile(server.podcastCoverArt).parentFile)
        }
        val restored = LibraryStore(context)
        assertEquals(store.library.value.episodes, restored.library.value.episodes)
        for (episode in restored.library.value.episodes) {
            val bitmap = readCover(restored.coverFile(episode.coverArtId))!!
            assertTrue(bitmap.width <= 512 && bitmap.height <= 512)
            bitmap.recycle()
        }
        // Existing installations have no artwork field until their next sync.
        val file = File(context.filesDir, "library.json")
        val json = JSONObject(file.readText())
        val entries = json.getJSONArray("episodes")
        for (index in 0 until entries.length()) entries.getJSONObject(index).remove("coverArtId")
        file.writeText(json.toString())
        assertTrue(LibraryStore(context).library.value.episodes.all { it.coverArtId.isEmpty() })
    }

    @Test fun unavailableOrInvalidCoverDoesNotFailSyncOrOverwriteCachedImage() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val store = LibraryStore(isolatedContext())
            server.ids = emptyList()
            server.podcastCoverArt = "pod-7"
            server.podcastEpisodes = listOf(episode("1"))
            val original = podcastCoverPng(Color.GREEN)
            server.coverImages["pod-7"] = original
            sync(store, server)
            server.coverImages.clear()
            sync(store, server)
            assertArrayEquals(original, store.coverFile("pod-7").readBytes())
            for (invalid in listOf("{\"subsonic-response\":{\"status\":\"failed\"}}".toByteArray(), ByteArray(0), ByteArray(5 * 1024 * 1024 + 1))) {
                server.coverImages["pod-7"] = invalid
                sync(store, server)
                assertArrayEquals(original, store.coverFile("pod-7").readBytes())
                assertFalse(File(store.coverFile("pod-7").path + ".part").exists())
            }
            val updated = podcastCoverPng(Color.BLUE)
            server.coverImages["pod-7"] = updated
            sync(store, server)
            assertArrayEquals(updated, store.coverFile("pod-7").readBytes())
            assertEquals(listOf("stream-1"), server.downloads)
            store.coverFile("pod-7").delete()
            server.coverImages.clear()
            sync(store, server)
            assertEquals(1, store.library.value.episodes.size)
            assertNull(readCover(store.coverFile("pod-7")))
        }
    }

    @Test fun obsoleteCoversAreRemovedOnlyAfterSuccessfulSync() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val store = LibraryStore(isolatedContext())
            server.ids = emptyList()
            server.podcastCoverArt = "old-cover"
            server.coverImages["old-cover"] = podcastCoverPng(Color.GREEN)
            server.podcastEpisodes = listOf(episode("1"))
            sync(store, server)
            server.podcastCoverArt = "new-cover"
            server.coverImages["new-cover"] = podcastCoverPng(Color.BLUE)
            server.podcastEpisodes = listOf(episode("2"))
            server.failDownload = true
            assertTrue(runCatching { sync(store, server) }.isFailure)
            assertTrue(store.coverFile("old-cover").exists())
            assertEquals("old-cover", store.library.value.episodes.single().coverArtId)
            server.failDownload = false
            sync(store, server)
            assertFalse(store.coverFile("old-cover").exists())
            assertTrue(store.coverFile("new-cover").exists())
            SyncEngine(store, SubsonicClient(server.settings.copy(podcastId = null))).run(server.settings.account) { _, _, _ -> }
            assertTrue(store.coverDirectory.listFiles()!!.isEmpty())
        }
    }

    @Test fun cancellingCoverDownloadPreservesLibraryAndCachedCover() = runBlocking(Dispatchers.IO) {
        FakeServer().use { server ->
            val store = LibraryStore(isolatedContext())
            server.ids = emptyList()
            server.podcastCoverArt = "pod-7"
            server.coverImages["pod-7"] = podcastCoverPng(Color.GREEN)
            server.podcastEpisodes = listOf(episode("1"))
            sync(store, server)
            val before = store.library.value
            val cover = store.coverFile("pod-7").readBytes()
            server.stalledCover = "pod-7"
            val client = SubsonicClient(server.settings)
            val job = async { SyncEngine(store, client).run(server.settings.account) { _, _, _ -> } }
            assertTrue(server.downloadStalled.await(5, TimeUnit.SECONDS))
            client.cancel()
            job.cancel()
            withTimeout(5000) { job.join() }
            assertEquals(before, store.library.value)
            assertArrayEquals(cover, store.coverFile("pod-7").readBytes())
            assertFalse(File(store.coverFile("pod-7").path + ".part").exists())
        }
    }

    private suspend fun sync(store: LibraryStore, server: FakeServer) =
        SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> }

    private fun episode(id: String) = JSONObject().put("id", id).put("streamId", "stream-$id")
        .put("title", "Episode $id").put("status", "completed").put("duration", 60)
        .put("publishDate", "2020-01-0${id}T00:00:00Z")

    private fun isolatedContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = UUID.randomUUID().toString()
        val directory = File(context.cacheDir, name).apply { mkdirs() }
        return object : ContextWrapper(context) {
            override fun getFilesDir() = directory
            override fun getSharedPreferences(key: String, mode: Int) = context.getSharedPreferences("$name.$key", mode)
        }
    }
}

internal fun podcastCoverPng(color: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888)
    return try {
        bitmap.eraseColor(color)
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    } finally { bitmap.recycle() }
}
