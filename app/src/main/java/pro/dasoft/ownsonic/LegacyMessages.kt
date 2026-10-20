package pro.dasoft.ownsonic

import android.content.res.Resources

// Read-only compatibility for WorkManager records written before structured message codes.
internal fun legacyLocalizedMessage(resources: Resources, message: String?, fallback: Int = R.string.generic_error): String {
    val resource = when (message) {
        "Could not save the rating." -> R.string.save_rating_error
        "Starting sync…" -> R.string.sync_starting
        "Waiting to start…" -> R.string.sync_waiting
        "Sync interrupted. Tap Sync to retry.", "Sync cancelled. Tap Sync to retry." -> R.string.sync_interrupted
        "No synchronization yet." -> R.string.sync_none
        "Select an available music playlist in Settings." -> R.string.choose_playlist_error
        "Choose ratings between 1 and 5 stars." -> R.string.rating_range_error
        "Choose between 1 and 50 podcast episodes." -> R.string.episode_limit_error
        "Could not save settings." -> R.string.save_settings_error
        "Could not save reminder preference." -> R.string.reminder_error
        "Could not save automatic synchronization preference." -> R.string.automatic_error
        "Could not connect to the player. Reopen the app to retry." -> R.string.player_connection_error
        "Automatic sync disabled." -> R.string.automatic_disabled
        "No local changes to synchronize." -> R.string.no_changes
        "Reading laterlist", "Reading music playlist" -> R.string.reading_playlist
        "Reading Aerostat", "Reading podcast" -> R.string.reading_podcast
        "A podcast episode has an invalid publication date." -> R.string.podcast_date_error
        "No new songs to download" -> R.string.no_downloads
        "Cannot reach the server. Check the address and internet connection." -> R.string.unreachable
        "The connection timed out. Try Sync again." -> R.string.timeout
        "Secure connection failed. Check the server certificate." -> R.string.ssl_error
        "Cannot connect to Airsonic. Check the server and network." -> R.string.connect_error
        "Sync failed. Try again." -> R.string.sync_failed
        "Enter a complete HTTP or HTTPS server URL without query parameters." -> R.string.url_error
        "Enter the server URL, username, and password first." -> R.string.credentials_error
        "Playlist 'laterlist' was not found." -> R.string.playlist_missing
        "More than one playlist is named 'laterlist'. Rename the duplicate." -> R.string.playlist_duplicate
        "The playlist must contain audio songs only." -> R.string.audio_only
        "The server returned an empty audio file." -> R.string.empty_audio
        "The server returned an error instead of an audio file. Check download permissions." -> R.string.download_error
        "The audio download was incomplete. Try Sync again." -> R.string.incomplete_audio
        "Could not save the downloaded song." -> R.string.save_audio_error
        "Invalid API response. Check the Airsonic server URL." -> R.string.invalid_response
        "Authentication failed. Check your username and password." -> R.string.authentication_error
        "The account does not have permission for this operation." -> R.string.permission_error
        "The requested song or playlist no longer exists." -> R.string.item_missing
        "The server redirected the request. Enter its final URL in Settings." -> R.string.redirect_error
        "This library belongs to another server or user. Keep the current account for now." -> R.string.account_error
        else -> null
    }
    if (resource != null) return resources.getString(resource)
    for ((pattern, id) in messagePatterns) {
        val match = pattern.matchEntire(message.orEmpty()) ?: continue
        val arguments = match.groupValues.drop(1).mapIndexed { index, value ->
            if (id == R.string.api_error || (id == R.string.downloading && index == 2)) value
            else value.toIntOrNull() ?: return resources.getString(fallback)
        }.toTypedArray<Any>()
        return resources.getString(id, *arguments)
    }
    return resources.getString(fallback)
}

private val messagePatterns = listOf(
    Regex("Sync complete\\. (\\d+) songs and (\\d+) episodes ready offline\\.") to R.string.sync_complete_all,
    Regex("Sync complete\\. (\\d+) songs ready offline\\.") to R.string.sync_complete,
    Regex("Uploading ratings (\\d+)/(\\d+)") to R.string.upload_ratings,
    Regex("Uploading listens (\\d+)/(\\d+)") to R.string.upload_listens,
    Regex("Downloading (\\d+)/(\\d+): ([\\s\\S]*)") to R.string.downloading,
    Regex("Airsonic API error \\(code (.*?)\\)\\.") to R.string.api_error,
    Regex("Server returned HTTP (\\d+)\\. Check the connection and server URL\\.") to R.string.http_error,
)
