package pro.dasoft.ownsonic

import android.content.res.Resources
import androidx.work.Data
import pro.dasoft.ownsonic.data.AppFailure
import pro.dasoft.ownsonic.data.AppMessage
import pro.dasoft.ownsonic.data.MessageCode

internal fun AppMessage.toWorkData(completed: Int = 0, total: Int = 0): Data = Data.Builder()
    .putString("messageCode", code.name)
    .putStringArray("messageArguments", arguments.toTypedArray())
    .putInt("completed", completed).putInt("total", total).build()

internal fun Data.appMessage(): AppMessage? {
    val code = getString("messageCode") ?: return null
    return runCatching { AppMessage(MessageCode.valueOf(code), getStringArray("messageArguments")?.toList().orEmpty()) }.getOrNull()
}

internal fun localizedMessage(resources: Resources, error: Throwable, fallback: Int = R.string.generic_error): String =
    (error as? AppFailure)?.let { localizedMessage(resources, it.userMessage, fallback) } ?: resources.getString(fallback)

internal fun localizedMessage(resources: Resources, message: AppMessage, fallback: Int = R.string.generic_error): String {
    val resource = when (message.code) {
        MessageCode.SAVE_RATING_ERROR -> R.string.save_rating_error
        MessageCode.SYNC_STARTING -> R.string.sync_starting
        MessageCode.SYNC_WAITING -> R.string.sync_waiting
        MessageCode.SYNC_INTERRUPTED -> R.string.sync_interrupted
        MessageCode.SYNC_NONE -> R.string.sync_none
        MessageCode.CHOOSE_PLAYLIST_ERROR -> R.string.choose_playlist_error
        MessageCode.RATING_RANGE_ERROR -> R.string.rating_range_error
        MessageCode.EPISODE_LIMIT_ERROR -> R.string.episode_limit_error
        MessageCode.SAVE_SETTINGS_ERROR -> R.string.save_settings_error
        MessageCode.REMINDER_ERROR -> R.string.reminder_error
        MessageCode.AUTOMATIC_ERROR -> R.string.automatic_error
        MessageCode.PLAYER_CONNECTION_ERROR -> R.string.player_connection_error
        MessageCode.AUTOMATIC_DISABLED -> R.string.automatic_disabled
        MessageCode.NO_CHANGES -> R.string.no_changes
        MessageCode.READING_PLAYLIST -> R.string.reading_playlist
        MessageCode.READING_PODCAST -> R.string.reading_podcast
        MessageCode.PODCAST_DATE_ERROR -> R.string.podcast_date_error
        MessageCode.NO_DOWNLOADS -> R.string.no_downloads
        MessageCode.UNREACHABLE -> R.string.unreachable
        MessageCode.TIMEOUT -> R.string.timeout
        MessageCode.SSL_ERROR -> R.string.ssl_error
        MessageCode.CONNECT_ERROR -> R.string.connect_error
        MessageCode.SYNC_FAILED -> R.string.sync_failed
        MessageCode.URL_ERROR -> R.string.url_error
        MessageCode.CREDENTIALS_ERROR -> R.string.credentials_error
        MessageCode.AUDIO_ONLY -> R.string.audio_only
        MessageCode.EMPTY_AUDIO -> R.string.empty_audio
        MessageCode.DOWNLOAD_ERROR -> R.string.download_error
        MessageCode.INCOMPLETE_AUDIO -> R.string.incomplete_audio
        MessageCode.SAVE_AUDIO_ERROR -> R.string.save_audio_error
        MessageCode.INVALID_RESPONSE -> R.string.invalid_response
        MessageCode.AUTHENTICATION_ERROR -> R.string.authentication_error
        MessageCode.PERMISSION_ERROR -> R.string.permission_error
        MessageCode.ITEM_MISSING -> R.string.item_missing
        MessageCode.REDIRECT_ERROR -> R.string.redirect_error
        MessageCode.ACCOUNT_ERROR -> R.string.account_error
        MessageCode.SYNC_COMPLETE_ALL -> R.string.sync_complete_all
        MessageCode.UPLOAD_RATINGS -> R.string.upload_ratings
        MessageCode.UPLOAD_LISTENS -> R.string.upload_listens
        MessageCode.DOWNLOADING -> R.string.downloading
        MessageCode.API_ERROR -> R.string.api_error
        MessageCode.HTTP_ERROR -> R.string.http_error
        MessageCode.LIBRARY_UNAVAILABLE -> R.string.library_unavailable
        MessageCode.CREDENTIALS_UNAVAILABLE -> R.string.credentials_unavailable
    }
    val arguments = message.arguments.mapIndexed { index, value ->
        when (message.code) {
            MessageCode.DOWNLOADING -> if (index < 2) value.toIntOrNull() ?: return resources.getString(fallback) else value
            MessageCode.API_ERROR -> value
            else -> value.toIntOrNull() ?: return resources.getString(fallback)
        }
    }.toTypedArray<Any>()
    return try { resources.getString(resource, *arguments) }
    catch (_: java.util.IllegalFormatException) { resources.getString(fallback) }
}
