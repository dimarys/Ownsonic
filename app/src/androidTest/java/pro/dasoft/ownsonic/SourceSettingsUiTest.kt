package pro.dasoft.ownsonic

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pro.dasoft.ownsonic.data.LibraryStore
import pro.dasoft.ownsonic.data.ServerSource
import pro.dasoft.ownsonic.data.ServerSettings
import pro.dasoft.ownsonic.data.Song

@RunWith(AndroidJUnit4::class)
class SourceSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun loadedSourcesCanBeSelectedValidatedSavedAndReopenedOffline() {
        FakeServer().use { server ->
            server.playlistSources = listOf(ServerSource("original", "Original playlist"), ServerSource("road", "Road mix"))
            server.podcastChannelId = "42"
            server.podcastTitle = "Science daily"
            val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
            val settings = server.settings.copy(playlistId = "original", playlistName = "Original playlist", podcastId = null)
            compose.runOnIdle {
                store.replaceQueue(settings.account, listOf(Song("source-settings-song", "Cached song", "Artist", "Album", 60000)))
                store.saveSettings(settings)
            }
            val before = store.library.value
            compose.onNodeWithText("Settings").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText(text(R.string.server_title)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(text(R.string.load_sources)).performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText(text(R.string.sources_loaded)).fetchSemanticsNodes().isNotEmpty() }
            clickControl(text(R.string.music_playlist))
            compose.onNodeWithText("Road mix").performClick()
            clickControl(text(R.string.podcast_source))
            compose.onNodeWithText("Science daily").performClick()
            compose.onNodeWithText(text(R.string.episode_limit)).performScrollTo().performTextReplacement("0")
            compose.onNodeWithText(text(R.string.save_settings)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.episode_limit_error)).assertExists()
            assertEquals(settings, store.settings())
            compose.onNodeWithText(text(R.string.episode_limit)).performScrollTo().performTextReplacement("3")
            clickControl(text(R.string.podcast_selection))
            compose.onNodeWithText(text(R.string.newest_episodes)).performClick()
            clickControl(text(R.string.like_rating))
            compose.onNodeWithText(compose.activity.getString(R.string.rating_value, 5)).performClick()
            clickControl(text(R.string.dislike_rating))
            compose.onNodeWithText(compose.activity.getString(R.string.rating_value, 3)).performClick()
            compose.onNodeWithText(text(R.string.save_settings)).performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText(text(R.string.settings_saved)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(text(R.string.settings_saved)).assertExists()
            assertEquals("road", store.settings().playlistId)
            assertEquals("42", store.settings().podcastId)
            assertEquals(3, store.settings().episodeLimit)
            assertEquals(5, store.settings().likeRating)
            assertEquals(3, store.settings().dislikeRating)
            assertTrue(store.settings().newestEpisodes)
            assertEquals(before, store.library.value)
            compose.onNodeWithText("Music").performClick()
            compose.onNodeWithText("Settings").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText(text(R.string.server_title)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Road mix").performScrollTo().assertExists()
            compose.onNodeWithText("Science daily").performScrollTo().assertExists()
            compose.onNodeWithText(text(R.string.newest_episodes)).performScrollTo().assertExists()
            compose.onNodeWithText(compose.activity.getString(R.string.rating_value, 5)).performScrollTo().assertExists()
            compose.onNodeWithText(compose.activity.getString(R.string.rating_value, 3)).performScrollTo().assertExists()
            clickControl(text(R.string.podcast_source))
            compose.onNodeWithText(text(R.string.no_podcast)).performClick()
            compose.onNodeWithText(text(R.string.save_settings)).performScrollTo().performClick()
            compose.waitUntil(5000) { store.settings().podcastId == null }
            assertNull(store.settings().podcastId)
            assertEquals(before, store.library.value)
        }
    }

    @Test fun firstSetupRequiresPlaylistSelectionAndAllowsMusicOnlyServers() {
        FakeServer().use { server ->
            server.playlistSources = listOf(ServerSource("alpha", "Alpha mix"), ServerSource("beta", "Beta mix"))
            server.failPodcasts = true
            val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
            compose.runOnIdle {
                store.replaceQueue("", emptyList())
                store.saveSettings(ServerSettings(podcastId = null))
            }
            compose.onNodeWithText("Settings").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText(text(R.string.server_title)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(text(R.string.server_url)).performScrollTo().performTextReplacement(server.settings.url)
            compose.onNodeWithText(text(R.string.username)).performScrollTo().performTextReplacement(server.settings.username)
            compose.onNodeWithText(text(R.string.password)).performScrollTo().performTextReplacement(server.settings.password)
            compose.onNodeWithText(text(R.string.load_sources)).performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Playlists loaded.", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(text(R.string.save_settings)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.choose_playlist_error)).assertExists()
            assertFalse(store.settings().configured)
            clickControl(text(R.string.music_playlist))
            compose.onNodeWithText("Beta mix").performClick()
            compose.onNodeWithText(text(R.string.save_settings)).performScrollTo().performClick()
            compose.waitUntil(5000) { store.settings().configured }
            assertTrue(store.settings().configured)
            assertEquals("beta", store.settings().playlistId)
            compose.waitUntil(5000) { store.settings().podcastId == null }
            assertNull(store.settings().podcastId)
        }
    }

    @Test fun configuredRatingButtonsApplySavedStarsAndPreserveSkipBehavior() {
        FakeServer().use { server ->
            val store = LibraryStore.get(ApplicationProvider.getApplicationContext<Context>())
            val settings = server.settings.copy(likeRating = 5, dislikeRating = 3)
            val songs = listOf(Song("rating-choice-a", "Choice A", "Artist", "Album", 60000),
                Song("rating-choice-b", "Choice B", "Artist", "Album", 60000))
            val audio = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
                .open("embedded-cover.mp3").use { it.readBytes() }
            songs.forEach { store.audioFile(it.id).writeBytes(audio) }
            compose.runOnIdle {
                store.replaceQueue(settings.account, songs)
                store.saveSettings(settings)
            }
            val first = store.library.value.currentId
            val like = compose.activity.getString(R.string.like, 5)
            val dislike = compose.activity.getString(R.string.dislike, 3)
            compose.waitUntil(10000) { compose.onAllNodes(hasContentDescription(like) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            clickControl(like)
            compose.waitUntil(5000) { store.library.value.pending[first] == 5 }
            assertEquals(first, store.library.value.currentId)
            clickControl(dislike)
            try {
                compose.waitUntil(5000) { store.library.value.pending[first] == 3 && store.library.value.currentId != first }
            } catch (failure: Throwable) {
                throw AssertionError("Rating navigation failed: first=$first, library=${store.library.value}, writeFailure=${store.writeFailure.value}", failure)
            }
        }
    }

    @Test fun unreadablePasswordCanBeReplacedWithoutClearingLibrary() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LibraryStore.get(context)
        val settings = ServerSettings("https://example.com", "listener", "old-password",
            playlistId = "road", playlistName = "Road mix", podcastId = null)
        compose.runOnIdle {
            store.replaceQueue(settings.account, listOf(Song("credential-song", "Cached song", "Artist", "Album", 60000)))
            store.saveSettings(settings)
            context.getSharedPreferences("connection", 0).edit().putString("password", "damaged").commit()
        }
        val before = store.library.value
        compose.onNodeWithText("Settings").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(text(R.string.credentials_unavailable)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.password)).performScrollTo().performTextReplacement("replacement-password")
        compose.onNodeWithText(text(R.string.save_settings)).performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(text(R.string.settings_saved)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.credentials_unavailable)).assertDoesNotExist()
        assertEquals(settings.copy(password = "replacement-password"), store.settings())
        assertEquals(before, store.library.value)
    }

    private fun clickControl(label: String) {
        compose.waitUntil(10000) {
            compose.onAllNodes(hasContentDescription(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
    }

    private fun text(id: Int) = compose.activity.getString(id)
}
