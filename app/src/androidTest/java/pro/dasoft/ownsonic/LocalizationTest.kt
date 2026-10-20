package pro.dasoft.ownsonic

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import pro.dasoft.ownsonic.data.AppMessage
import pro.dasoft.ownsonic.data.MessageCode
import androidx.work.Data

@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun persistedSyncMessagesFollowLocaleAndPreserveSongTitles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun resources(language: String) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        ).resources
        val russian = resources("ru")
        val english = resources("en")
        assertEquals("Скачивание 1/3: Road song", legacyLocalizedMessage(russian, "Downloading 1/3: Road song"))
        assertEquals("Отправка оценок 2/4", legacyLocalizedMessage(russian, "Uploading ratings 2/4"))
        assertEquals("Отправка прослушиваний 1/2", legacyLocalizedMessage(russian, "Uploading listens 1/2"))
        assertEquals(russian.getString(R.string.sync_complete_all, 12, 10),
            legacyLocalizedMessage(russian, "Sync complete. 12 songs and 10 episodes ready offline."))
        assertEquals("Синхронизация завершена. Песен для прослушивания без интернета: 12.",
            legacyLocalizedMessage(russian, "Sync complete. 12 songs ready offline."))
        assertEquals("Ошибка API Airsonic (код unknown).", legacyLocalizedMessage(russian, "Airsonic API error (code unknown)."))
        assertEquals(russian.getString(R.string.http_error, 403),
            legacyLocalizedMessage(russian, "Server returned HTTP 403. Check the connection and server URL."))
        assertEquals(russian.getString(R.string.authentication_error),
            legacyLocalizedMessage(russian, "Authentication failed. Check your username and password."))
        assertEquals("Downloading 1/3: Песня", legacyLocalizedMessage(english, "Downloading 1/3: Песня"))
        assertEquals("Settings", resources("fr").getString(R.string.settings))
        assertEquals(russian.getString(R.string.generic_error), legacyLocalizedMessage(russian, "Internal disk error"))
    }

    @Test fun structuredWorkerMessagesUseCodesAndPreserveArguments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val russian = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru")) }
        ).resources
        val title = "A title: 100% / \nПесня"
        val message = AppMessage(MessageCode.DOWNLOADING, 2, 7, title)
        val data = message.toWorkData(1, 7)
        assertEquals(message, data.appMessage())
        assertEquals(russian.getString(R.string.downloading, 2, 7, title), localizedMessage(russian, data.appMessage()!!))
        val changedDiagnostic = Data.Builder().putAll(data).putString("message", "Completely different English text").build()
        assertEquals(message, changedDiagnostic.appMessage())
        assertEquals(russian.getString(R.string.authentication_error),
            localizedMessage(russian, AppMessage(MessageCode.AUTHENTICATION_ERROR)))
        assertNull(Data.Builder().putString("messageCode", "FUTURE_CODE").build().appMessage())
        assertEquals(russian.getString(R.string.generic_error),
            localizedMessage(russian, AppMessage(MessageCode.DOWNLOADING, "invalid", 7, title)))
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test fun systemAppLanguageSwitchUpdatesAllScreens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(LocaleManager::class.java)
        val original = manager.applicationLocales
        try {
            manager.applicationLocales = LocaleList.forLanguageTags("ru")
            compose.waitUntil(10000) { compose.onAllNodesWithText("Настройки").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Музыка").assertExists()
            compose.onNodeWithText("Подкаст").performClick()
            compose.onNodeWithText("Отметки о прослушивании хранятся на телефоне.", substring = true).assertExists()
            File(context.cacheDir, "russian-aerostat.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("Синхр.").performClick()
            compose.onNodeWithText("Всё готово к поездке").assertExists()
            compose.onNodeWithText("🔄 Синхронизировать").performScrollTo().assertExists()
            compose.onNodeWithText("Настройки").performClick()
            compose.onNodeWithText("Ваш сервер Airsonic").assertExists()
            compose.onNodeWithText("Сохранить настройки").performScrollTo().assertExists()
            compose.onNodeWithContentDescription("Напоминание об оценке").performScrollTo().assertExists()
            compose.onNodeWithContentDescription("Автоматическая синхронизация").performScrollTo().assertExists()
            File(context.cacheDir, "russian-settings.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            manager.applicationLocales = LocaleList.forLanguageTags("en")
            compose.waitUntil(10000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Your Airsonic server").assertExists()
        } finally {
            manager.applicationLocales = original
        }
    }
}
