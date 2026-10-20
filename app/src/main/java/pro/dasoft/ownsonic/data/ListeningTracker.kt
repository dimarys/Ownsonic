package pro.dasoft.ownsonic.data

import java.util.UUID

/** Tracks actual playback time; buffering, pauses, and seeking do not count. */
data class ListeningSession(
    val eventId: String = UUID.randomUUID().toString(),
    val songId: String,
    val revision: Long,
    val startedAt: Long = 0,
    val playedMillis: Long = 0,
    val seeked: Boolean = false,
    val reminderPlayed: Boolean = false,
)

data class Scrobble(val eventId: String, val songId: String, val startedAt: Long)

class ListeningTracker(restored: ListeningSession? = null) {
    var session: ListeningSession? = restored
        private set
    private var lastElapsed: Long? = null
    private var wasPlaying = false

    fun sample(songId: String, revision: Long, playing: Boolean, wallTime: Long, elapsed: Long) {
        accrue(elapsed)
        if (session?.songId != songId || session?.revision != revision) {
            session = ListeningSession(songId = songId, revision = revision)
        }
        if (playing && session?.startedAt == 0L) session = session?.copy(startedAt = wallTime)
        wasPlaying = playing
        lastElapsed = elapsed
    }

    fun seek(position: Long, elapsed: Long) {
        accrue(elapsed)
        session = if (position == 0L) session?.let { ListeningSession(songId = it.songId, revision = it.revision) }
        else session?.copy(seeked = true)
        wasPlaying = false
        lastElapsed = elapsed
    }

    fun complete(duration: Long, elapsed: Long): Scrobble? {
        accrue(elapsed)
        val current = session
        session = null
        wasPlaying = false
        lastElapsed = elapsed
        if (current == null || current.seeked || current.startedAt == 0L || duration <= 30_000 ||
            current.playedMillis < minOf(duration / 2, 240_000)) return null
        return Scrobble(current.eventId, current.songId, current.startedAt)
    }

    fun reset() { session = null; wasPlaying = false; lastElapsed = null }

    fun reminderDue(enabled: Boolean, playing: Boolean, rating: Int, duration: Long, position: Long): Boolean {
        val current = session ?: return false
        return enabled && playing && rating == 0 && current.startedAt > 0 && !current.reminderPlayed &&
            duration > 20_000 && position >= 0 && duration - position in 1..20_000
    }

    fun markReminderPlayed() { session = session?.copy(reminderPlayed = true) }

    private fun accrue(elapsed: Long) {
        val previous = lastElapsed
        if (wasPlaying && previous != null && elapsed >= previous) {
            session = session?.copy(playedMillis = session!!.playedMillis + elapsed - previous)
        }
        lastElapsed = elapsed
    }
}
