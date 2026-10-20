package pro.dasoft.ownsonic

import org.junit.Assert.*
import org.junit.Test
import pro.dasoft.ownsonic.data.ListeningTracker

class ListeningTrackerTest {
    @Test fun reminderRequiresEnabledUnratedPlaybackWithTwentySecondsRemaining() {
        val tracker = ListeningTracker()
        tracker.sample("a", 1, true, 100_000, 0)
        assertFalse(tracker.reminderDue(true, true, 0, 60_000, 39_999))
        assertTrue(tracker.reminderDue(true, true, 0, 60_000, 40_000))
        assertFalse(tracker.reminderDue(false, true, 0, 60_000, 40_000))
        assertFalse(tracker.reminderDue(true, false, 0, 60_000, 40_000))
        assertFalse(tracker.reminderDue(true, true, 1, 60_000, 40_000))
        assertFalse(tracker.reminderDue(true, true, 2, 60_000, 40_000))
        assertFalse(tracker.reminderDue(true, true, 0, 60_000, 60_000))
        assertFalse(tracker.reminderDue(true, true, 0, 20_000, 0))
    }

    @Test fun reminderDoesNotRepeatAfterPauseSeekOrRestorationButRestartResetsIt() {
        val tracker = ListeningTracker()
        tracker.sample("a", 1, true, 100_000, 0)
        tracker.markReminderPlayed()
        tracker.sample("a", 1, false, 110_000, 10_000)
        tracker.seek(40_000, 20_000)
        tracker.sample("a", 1, true, 120_000, 20_000)
        assertFalse(tracker.reminderDue(true, true, 0, 60_000, 40_000))
        val restored = ListeningTracker(tracker.session)
        restored.sample("a", 1, true, 130_000, 30_000)
        assertFalse(restored.reminderDue(true, true, 0, 60_000, 40_000))
        restored.seek(0, 40_000)
        restored.sample("a", 1, true, 140_000, 40_000)
        assertTrue(restored.reminderDue(true, true, 0, 60_000, 40_000))
        restored.markReminderPlayed()
        restored.sample("b", 1, true, 150_000, 50_000)
        assertTrue(restored.reminderDue(true, true, 0, 60_000, 40_000))
    }

    @Test fun naturalCompletionUsesStartTimeAndCountsPausesCorrectly() {
        val tracker = ListeningTracker()
        tracker.sample("a", 1, true, 100_000, 0)
        tracker.sample("a", 1, false, 120_000, 20_000)
        tracker.sample("a", 1, true, 180_000, 80_000)
        tracker.sample("a", 1, false, 220_000, 120_000)
        val event = tracker.complete(60_000, 120_000)!!
        assertEquals(100_000, event.startedAt)
        assertEquals("a", event.songId)
        assertNull(tracker.complete(60_000, 120_001))
    }

    @Test fun seekingToEndDoesNotCreateListen() {
        val tracker = ListeningTracker()
        tracker.sample("a", 1, true, 100_000, 0)
        tracker.seek(60_000, 40_000)
        assertNull(tracker.complete(60_000, 40_001))
    }

    @Test fun shortTrackAndInsufficientActualPlaybackAreExcluded() {
        val tracker = ListeningTracker()
        tracker.sample("a", 1, true, 100_000, 0)
        assertNull(tracker.complete(60_000, 10_000))
        tracker.sample("b", 1, true, 200_000, 10_000)
        assertNull(tracker.complete(30_000, 40_000))
    }

    @Test fun restorationDoesNotCountTimeWhileAppWasClosed() {
        val tracker = ListeningTracker()
        tracker.sample("a", 1, true, 100_000, 0)
        tracker.sample("a", 1, false, 120_000, 20_000)
        val restored = ListeningTracker(tracker.session)
        restored.sample("a", 1, true, 900_000, 800_000)
        assertEquals(20_000, restored.session!!.playedMillis)
        assertEquals(100_000, restored.complete(60_000, 840_000)!!.startedAt)
    }

    @Test fun repeatsCreateSeparateEventsAndStopStartsFreshSession() {
        val tracker = ListeningTracker()
        tracker.sample("a", 1, true, 100_000, 0)
        val first = tracker.complete(60_000, 60_000)!!
        tracker.sample("a", 1, true, 200_000, 60_000)
        val second = tracker.complete(60_000, 120_000)!!
        assertNotEquals(first.eventId, second.eventId)
        tracker.sample("a", 1, true, 300_000, 120_000)
        tracker.reset()
        tracker.sample("a", 1, true, 400_000, 140_000)
        assertEquals(0, tracker.session!!.playedMillis)
        assertEquals(400_000, tracker.session!!.startedAt)
    }
}
