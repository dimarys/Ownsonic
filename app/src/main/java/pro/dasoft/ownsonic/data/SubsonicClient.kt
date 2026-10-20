package pro.dasoft.ownsonic.data

import android.net.Uri
import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class ServerSource(val id: String, val name: String)
data class ServerCatalog(val playlists: List<ServerSource>, val podcasts: List<ServerSource>, val podcastError: Throwable? = null)

class SubsonicClient(private val settings: ServerSettings, private val network: Network? = null) {
    private val connectionLock = Any()
    private var activeConnection: HttpURLConnection? = null
    private var cancelled = false

    fun cancel() {
        val connection = synchronized(connectionLock) {
            cancelled = true
            activeConnection.also { activeConnection = null }
        }
        connection?.disconnect()
    }

    private fun release(connection: HttpURLConnection) {
        synchronized(connectionLock) {
            if (activeConnection === connection) activeConnection = null
        }
        connection.disconnect()
    }

    init {
        val uri = URI(settings.url)
        require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null) {
            throw AppFailure(MessageCode.URL_ERROR)
        }
        require(settings.configured) { throw AppFailure(MessageCode.CREDENTIALS_ERROR) }
    }

    fun playlists(): List<ServerSource> {
        val entries = request("getPlaylists").optJSONObject("playlists")?.optJSONArray("playlist")
        return (0 until (entries?.length() ?: 0)).map {
            val entry = entries!!.getJSONObject(it)
            ServerSource(entry.getString("id"), entry.optString("name", "Untitled"))
        }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    fun podcasts(): List<ServerSource> {
        val entries = request("getPodcasts", mapOf("includeEpisodes" to "false"))
            .optJSONObject("podcasts")?.optJSONArray("channel")
        return (0 until (entries?.length() ?: 0)).map {
            val entry = entries!!.getJSONObject(it)
            ServerSource(entry.getString("id"), entry.optString("title", "Untitled"))
        }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    suspend fun catalog(): ServerCatalog = withContext(Dispatchers.IO) {
        coroutineScope {
            val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { this@SubsonicClient.cancel() }
            }
            try {
                val playlists = playlists()
                try { ServerCatalog(playlists, podcasts()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    coroutineContext.ensureActive()
                    ServerCatalog(playlists, emptyList(), error)
                }
            } finally { watcher.cancel() }
        }
    }

    fun playlist(): List<Song> {
        val matches = playlists().filter {
            if (settings.playlistId.isNotBlank()) it.id == settings.playlistId else it.name == settings.playlistName
        }
        require(matches.size == 1) { throw AppFailure(MessageCode.CHOOSE_PLAYLIST_ERROR) }
        val entries = request("getPlaylist", mapOf("id" to matches.single().id))
            .getJSONObject("playlist").optJSONArray("entry")
        return (0 until (entries?.length() ?: 0)).map {
            val song = entries!!.getJSONObject(it)
            require(!song.optBoolean("isDir") && !song.optBoolean("isVideo")) {
                throw AppFailure(MessageCode.AUDIO_ONLY)
            }
            Song(song.getString("id"), song.optString("title", "Untitled"),
                song.optString("artist", "Unknown artist"), song.optString("album"),
                song.optLong("duration") * 1000, song.optInt("userRating"))
        }.distinctBy { it.id }
    }

    fun podcastEpisodes(): List<PodcastEpisode> {
        val id = settings.podcastId ?: return emptyList()
        require(settings.episodeLimit in 1..50) { throw AppFailure(MessageCode.EPISODE_LIMIT_ERROR) }
        val channels = request("getPodcasts", mapOf("id" to id, "includeEpisodes" to "true"))
            .getJSONObject("podcasts").optJSONArray("channel") ?: return emptyList()
        val channel = (0 until channels.length()).map { channels.getJSONObject(it) }
            .singleOrNull { it.getString("id") == id } ?: return emptyList()
        val episodes = channel.optJSONArray("episode") ?: return emptyList()
        return (0 until episodes.length()).map { episodes.getJSONObject(it) }
            .filter { it.optString("status") == "completed" && it.optString("streamId").isNotBlank() }
            .map { episode ->
                val publishedAt = try { java.time.Instant.parse(episode.getString("publishDate")).toEpochMilli() }
                    catch (_: Exception) { throw AppFailure(MessageCode.PODCAST_DATE_ERROR) }
                PodcastEpisode(episode.getString("id"), episode.getString("streamId"),
                    episode.optString("title", "Untitled"), publishedAt, episode.optLong("duration") * 1000,
                    channel.optString("title", settings.podcastName),
                    episode.optString("coverArt").takeIf { it.isNotBlank() } ?: channel.optString("coverArt"))
            }.distinctBy { it.id }
            .sortedWith(compareBy<PodcastEpisode> { it.publishedAt }.thenBy { it.id })
            .let { if (settings.newestEpisodes) it.takeLast(settings.episodeLimit) else it.take(settings.episodeLimit) }
    }

    fun setRating(id: String, rating: Int) {
        request("setRating", mapOf("id" to id, "rating" to rating.toString()))
    }

    fun scrobble(event: Scrobble) {
        request("scrobble", mapOf("id" to event.songId, "time" to event.startedAt.toString(), "submission" to "true"))
    }

    suspend fun downloadCover(id: String, destination: File) {
        val connection = connection("getCoverArt", mapOf("id" to id, "size" to "512"))
        val partial = File(destination.path + ".part")
        try {
            checkResponse(connection)
            val limit = 5 * 1024 * 1024
            if (connection.contentLengthLong > limit) throw IOException("Cover image is too large.")
            connection.inputStream.buffered().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > limit) throw IOException("Cover image is too large.")
                        output.write(buffer, 0, read)
                    }
                }
            }
            val expected = connection.contentLengthLong
            if (expected > 0 && partial.length() != expected) throw IOException("Cover image is incomplete.")
            val bitmap = readCover(partial) ?: throw IOException("Cover image is unreadable.")
            bitmap.recycle()
            coroutineContext.ensureActive()
            if (!partial.renameTo(destination)) throw IOException("Could not save cover image.")
        } finally {
            release(connection)
            partial.delete()
        }
    }

    suspend fun download(id: String, destination: File) {
        val connection = connection("download", mapOf("id" to id))
        val partial = File(destination.path + ".part")
        try {
            checkResponse(connection)
            connection.inputStream.buffered().use { input ->
                val header = ByteArray(512)
                val count = input.read(header)
                check(count > 0) { throw AppFailure(MessageCode.EMPTY_AUDIO) }
                val prefix = String(header, 0, count).trimStart()
                if (prefix.startsWith("<?xml") || prefix.startsWith("<subsonic-response") ||
                    prefix.startsWith("{") || prefix.startsWith("<!DOCTYPE") || prefix.startsWith("<html")) {
                    throw AppFailure(MessageCode.DOWNLOAD_ERROR)
                }
                partial.outputStream().use { output ->
                    output.write(header, 0, count)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            val expected = connection.contentLengthLong
            check(expected <= 0 || partial.length() == expected) { throw AppFailure(MessageCode.INCOMPLETE_AUDIO) }
            coroutineContext.ensureActive()
            check(partial.renameTo(destination)) { throw AppFailure(MessageCode.SAVE_AUDIO_ERROR) }
        } finally {
            release(connection)
            partial.delete()
        }
    }

    private fun request(method: String, parameters: Map<String, String> = emptyMap()): JSONObject {
        val connection = connection(method, parameters)
        try {
            checkResponse(connection)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val response = try { JSONObject(body).getJSONObject("subsonic-response") }
                catch (_: Exception) { throw AppFailure(MessageCode.INVALID_RESPONSE) }
            if (response.optString("status") != "ok") {
                val code = response.optJSONObject("error")?.optInt("code")
                throw AppFailure(when (code) {
                    40, 41 -> AppMessage(MessageCode.AUTHENTICATION_ERROR)
                    50 -> AppMessage(MessageCode.PERMISSION_ERROR)
                    70 -> AppMessage(MessageCode.ITEM_MISSING)
                    else -> AppMessage(MessageCode.API_ERROR, code ?: "unknown")
                })
            }
            return response
        } finally { release(connection) }
    }

    private fun connection(method: String, parameters: Map<String, String>): HttpURLConnection {
        val salt = UUID.randomUUID().toString()
        val token = MessageDigest.getInstance("MD5").digest((settings.password + salt).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val uri = Uri.parse(settings.url.trimEnd('/') + "/rest/$method.view").buildUpon()
        // All required endpoints and token authentication are available in API 1.13.0.
        (mapOf("u" to settings.username, "t" to token, "s" to salt, "v" to "1.13.0",
            "c" to "Ownsonic", "f" to "json") + parameters).forEach { (key, value) -> uri.appendQueryParameter(key, value) }
        val url = URI(uri.build().toString()).toURL()
        val connection = ((network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = false
        }
        synchronized(connectionLock) {
            if (cancelled) {
                connection.disconnect()
                throw CancellationException("Sync cancelled.")
            }
            activeConnection = connection
        }
        return connection
    }

    private fun checkResponse(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) throw AppFailure(
            if (code in 300..399) AppMessage(MessageCode.REDIRECT_ERROR)
            else AppMessage(MessageCode.HTTP_ERROR, code)
        )
    }
}
