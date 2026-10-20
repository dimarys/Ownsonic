package pro.dasoft.ownsonic

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pro.dasoft.ownsonic.data.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StorageRecoveryTest {
    private fun isolatedContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "recovery-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, name).apply { mkdirs() }
        return object : ContextWrapper(context) {
            override fun getFilesDir() = directory
            override fun getSharedPreferences(key: String, mode: Int) = context.getSharedPreferences("$name.$key", mode)
        }
    }

    @Test fun corruptLibraryBlocksSyncAndPreservesBytesUntilExplicitReset() = runBlocking(Dispatchers.IO) {
        val context = isolatedContext()
        val original = LibraryStore(context)
        val settings = ServerSettings("https://example.com", "listener", "test-password")
        original.saveSettings(settings)
        val cached = original.audioFile("cached").apply { writeText("audio") }
        val disk = File(context.filesDir, "library.json")
        val damaged = "{\"pending\": {\"cached\": 5}, broken"
        disk.writeText(damaged)
        val store = LibraryStore(context)
        assertTrue(store.loadFailure.value)
        FakeServer().use { server ->
            val error = runCatching { SyncEngine(store, SubsonicClient(server.settings)).run(server.settings.account) { _, _, _ -> } }.exceptionOrNull()
            assertEquals(MessageCode.LIBRARY_UNAVAILABLE, (error as AppFailure).userMessage.code)
            assertTrue(server.methods.isEmpty())
        }
        assertTrue(runCatching { store.replaceQueue(settings.account, emptyList()) }.isFailure)
        assertEquals(damaged, disk.readText())
        assertTrue(cached.exists())
        store.onIo { resetDamagedLibrary() }
        assertFalse(store.loadFailure.value)
        assertEquals(settings, store.settings())
        assertTrue(cached.exists())
        assertFalse(File(context.filesDir, "recovery").exists())
        assertFalse(LibraryStore(context).loadFailure.value)
        assertTrue(LibraryStore(context).library.value.pending.isEmpty())
    }

    @Test fun retryRestoresPendingChangesAndBookmarksWithoutReset() {
        val context = isolatedContext()
        val store = LibraryStore(context)
        store.replaceQueue("account", listOf(Song("a", "Title", "Artist", "Album", 60000)))
        store.rate("a", 5)
        store.enqueueScrobble(Scrobble("listen", "a", 123000))
        store.savePlayback("a", 12345, store.library.value.revision)
        val disk = File(context.filesDir, "library.json")
        val intact = disk.readBytes()
        disk.writeText("invalid")
        val failed = LibraryStore(context)
        assertTrue(failed.loadFailure.value)
        disk.writeBytes(intact)
        failed.retryLoadingLibrary()
        assertFalse(failed.loadFailure.value)
        assertEquals(store.library.value, failed.library.value)
        assertEquals(store.library.value, LibraryStore(context).library.value)
    }

    @Test fun invalidSessionAndPasswordDoNotPreventLibraryRecovery() {
        val context = isolatedContext()
        val store = LibraryStore(context)
        val settings = ServerSettings("https://example.com", "listener", "password")
        store.saveSettings(settings)
        context.getSharedPreferences("playback", 0).edit().putString("listening", "invalid").commit()
        context.getSharedPreferences("connection", 0).edit().putString("password", "invalid").commit()
        val restored = LibraryStore(context)
        assertNull(restored.listeningSession())
        val failure = runCatching { restored.settings() }.exceptionOrNull() as AppFailure
        assertEquals(MessageCode.CREDENTIALS_UNAVAILABLE, failure.userMessage.code)
        assertEquals(settings.copy(password = ""), restored.settings(includePassword = false))
        restored.saveSettings(settings)
        assertEquals(settings, restored.settings())
    }

    @Test fun anInFlightRatingWriteSurvivesUiCancellationWithoutBlockingMain() = runBlocking {
        val context = isolatedContext()
        val store = LibraryStore(context)
        store.onIo { replaceQueue("account", listOf(Song("a", "Title", "Artist", "Album", 60000))) }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val write = launch(Dispatchers.Main.immediate) {
            store.onIo {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "Test write was not released." }
                rate("a", 5)
            }
        }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            withTimeout(2000) {
                withContext(Dispatchers.Main.immediate) {
                    assertEquals(Looper.getMainLooper(), Looper.myLooper())
                }
            }
            write.cancel()
        } finally {
            release.countDown()
            write.join()
        }
        assertEquals(5, LibraryStore(context).library.value.pending["a"])
    }

    @Test fun obsoletePlaybackWritesCannotOverwriteNewQueue() = runBlocking {
        val store = LibraryStore(isolatedContext())
        store.onIo { replaceQueue("account", listOf(Song("old", "Old", "Artist", "Album", 60000))) }
        val oldRevision = store.library.value.revision
        val entered = CountDownLatch(1)
        // Drain earlier callbacks before exercising a stale callback after a queue replacement.
        store.enqueuePlaybackWrite(null) { entered.countDown() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        store.onIo { replaceQueue("account", listOf(Song("new", "New", "Artist", "Album", 60000))) }
        store.enqueuePlaybackWrite(oldRevision) { setFinished(true); savePlayback("old", 50000, oldRevision) }
        val drained = CompletableDeferred<Unit>()
        store.enqueuePlaybackWrite(null) { drained.complete(Unit) }
        withTimeout(5000) { drained.await() }
        assertEquals("new", store.library.value.currentId)
        assertEquals(0L, store.library.value.position)
        assertFalse(store.library.value.finished)
    }
}
