package pro.dasoft.ownsonic.data

import java.io.IOException

/** Stable codes cross worker, storage, and UI boundaries independently of English text. */
enum class MessageCode(val diagnosticTemplate: String) {
    SAVE_RATING_ERROR("Could not save the rating."),
    SYNC_STARTING("Starting sync…"),
    SYNC_WAITING("Waiting to start…"),
    SYNC_INTERRUPTED("Sync cancelled. Tap Sync to retry."),
    SYNC_NONE("No synchronization yet."),
    CHOOSE_PLAYLIST_ERROR("Select an available music playlist in Settings."),
    RATING_RANGE_ERROR("Choose ratings between 1 and 5 stars."),
    EPISODE_LIMIT_ERROR("Choose between 1 and 50 podcast episodes."),
    SAVE_SETTINGS_ERROR("Could not save settings."),
    REMINDER_ERROR("Could not save reminder preference."),
    AUTOMATIC_ERROR("Could not save automatic synchronization preference."),
    PLAYER_CONNECTION_ERROR("Could not connect to the player. Reopen the app to retry."),
    AUTOMATIC_DISABLED("Automatic sync disabled."),
    NO_CHANGES("No local changes to synchronize."),
    READING_PLAYLIST("Reading music playlist"),
    READING_PODCAST("Reading podcast"),
    PODCAST_DATE_ERROR("A podcast episode has an invalid publication date."),
    NO_DOWNLOADS("No new songs to download"),
    UNREACHABLE("Cannot reach the server. Check the address and internet connection."),
    TIMEOUT("The connection timed out. Try Sync again."),
    SSL_ERROR("Secure connection failed. Check the server certificate."),
    CONNECT_ERROR("Cannot connect to Airsonic. Check the server and network."),
    SYNC_FAILED("Sync failed. Try again."),
    URL_ERROR("Enter a complete HTTP or HTTPS server URL without query parameters."),
    CREDENTIALS_ERROR("Enter the server URL, username, and password first."),
    AUDIO_ONLY("The playlist must contain audio songs only."),
    EMPTY_AUDIO("The server returned an empty audio file."),
    DOWNLOAD_ERROR("The server returned an error instead of an audio file. Check download permissions."),
    INCOMPLETE_AUDIO("The audio download was incomplete. Try Sync again."),
    SAVE_AUDIO_ERROR("Could not save the downloaded song."),
    INVALID_RESPONSE("Invalid API response. Check the Airsonic server URL."),
    AUTHENTICATION_ERROR("Authentication failed. Check your username and password."),
    PERMISSION_ERROR("The account does not have permission for this operation."),
    ITEM_MISSING("The requested song or playlist no longer exists."),
    REDIRECT_ERROR("The server redirected the request. Enter its final URL in Settings."),
    ACCOUNT_ERROR("This library belongs to another server or user. Keep the current account for now."),
    SYNC_COMPLETE_ALL("Sync complete. %s songs and %s episodes ready offline."),
    UPLOAD_RATINGS("Uploading ratings %s/%s"),
    UPLOAD_LISTENS("Uploading listens %s/%s"),
    DOWNLOADING("Downloading %s/%s: %s"),
    API_ERROR("Airsonic API error (code %s)."),
    HTTP_ERROR("Server returned HTTP %s. Check the connection and server URL."),
    LIBRARY_UNAVAILABLE("The local library could not be read. Restore it before syncing."),
    CREDENTIALS_UNAVAILABLE("The saved password could not be read. Enter it again in Settings."),
}

data class AppMessage(val code: MessageCode, val arguments: List<String> = emptyList()) {
    constructor(code: MessageCode, vararg arguments: Any) : this(code, arguments.map { it.toString() })

    val diagnostic: String get() = String.format(java.util.Locale.ROOT, code.diagnosticTemplate, *arguments.toTypedArray())
}

class AppFailure(val userMessage: AppMessage, cause: Throwable? = null) : IOException(userMessage.diagnostic, cause) {
    constructor(code: MessageCode, cause: Throwable? = null) : this(AppMessage(code), cause)
}
