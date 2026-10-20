package pro.dasoft.ownsonic.data

data class ServerSettings(
    val url: String = "", val username: String = "", val password: String = "",
    val playlistId: String = "", val playlistName: String = "laterlist",
    val podcastId: String? = "7", val podcastName: String = "Aerostat", val episodeLimit: Int = 5,
    val likeRating: Int = 2, val dislikeRating: Int = 1, val newestEpisodes: Boolean = false,
) {
    val account: String get() = "${url.trimEnd('/')}/$username"
    val configured: Boolean get() = url.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
}

data class RatingActions(val like: Int = 2, val dislike: Int = 1)

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val rating: Int = 0,
)

enum class PlaybackSource { MUSIC, AEROSTAT }

data class PodcastEpisode(
    val id: String,
    val streamId: String,
    val title: String,
    val publishedAt: Long,
    val duration: Long,
    val podcastTitle: String = "Podcast",
    val coverArtId: String = "",
) {
    val audioId: String get() = "podcast:$id"
    fun asSong() = Song(audioId, title, podcastTitle, podcastTitle, duration)
}

data class Library(
    val account: String = "",
    val songs: List<Song> = emptyList(),
    val pending: Map<String, Int> = emptyMap(),
    val currentId: String = "",
    val position: Long = 0,
    val revision: Long = 0,
    val finished: Boolean = false,
    val scrobbles: List<Scrobble> = emptyList(),
    val recordedEvents: List<String> = emptyList(),
    val episodes: List<PodcastEpisode> = emptyList(),
    val heardEpisodes: Set<String> = emptySet(),
    val podcastCurrentId: String = "",
    val podcastPosition: Long = 0,
    val podcastFinished: Boolean = false,
    val source: PlaybackSource = PlaybackSource.MUSIC,
    val playOnLoad: Boolean = false,

)

