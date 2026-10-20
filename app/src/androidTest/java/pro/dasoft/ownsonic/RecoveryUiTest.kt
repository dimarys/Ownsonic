package pro.dasoft.ownsonic

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pro.dasoft.ownsonic.data.LibraryStore
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecoveryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun damagedLibraryRequiresConfirmationBeforeResetWithoutBackup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "recovery-ui-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, name).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = directory
            override fun getSharedPreferences(key: String, mode: Int) = context.getSharedPreferences("$name.$key", mode)
        }
        val file = File(directory, "library.json").apply { writeText("broken library") }
        val store = LibraryStore(isolated)
        compose.setContent { OwnsonicApp(store, WorkManager.getInstance(context), null, null) }
        compose.onNodeWithText(context.getString(R.string.library_unavailable)).assertExists()
        compose.onNodeWithText(context.getString(R.string.sync_button)).assertDoesNotExist()
        val reset = context.getString(R.string.reset_library)
        compose.onNodeWithText(reset).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.reset_library_confirmation)).assertExists()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        assertTrue(store.loadFailure.value)
        assertEquals("broken library", file.readText())
        compose.onNodeWithText(reset).performClick()
        compose.onAllNodes(hasText(reset) and hasClickAction()).onLast().performClick()
        compose.waitUntil(5000) { !store.loadFailure.value }
        compose.onNodeWithText(context.getString(R.string.empty_title)).assertExists()
        assertFalse(File(directory, "recovery").exists())
        assertFalse(LibraryStore(isolated).loadFailure.value)
    }
}
