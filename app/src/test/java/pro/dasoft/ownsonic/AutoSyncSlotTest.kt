package pro.dasoft.ownsonic

import org.junit.Assert.*
import org.junit.Test
import pro.dasoft.ownsonic.data.AutoSyncSlot
import java.time.Duration
import java.time.ZonedDateTime

class AutoSyncSlotTest {
    @Test fun slotsUseLocalWallClockAndMovePastTimesToTomorrow() {
        val now = ZonedDateTime.parse("2026-10-11T23:00:00+03:00[Europe/Moscow]")
        assertEquals("2026-10-11T23:30+03:00[Europe/Moscow]", AutoSyncSlot.EVENING.nextRun(now).toString())
        assertEquals("2026-10-12T05:00+03:00[Europe/Moscow]", AutoSyncSlot.MORNING.nextRun(now).toString())
        val exact = AutoSyncSlot.EVENING.nextRun(now)
        assertEquals(exact.plusDays(1), AutoSyncSlot.EVENING.nextRun(exact))
    }

    @Test fun daylightSavingKeepsFiveAmInsteadOfAddingTwentyFourHours() {
        val now = ZonedDateTime.parse("2026-03-28T05:00:00+01:00[Europe/Berlin]")
        val next = AutoSyncSlot.MORNING.nextRun(now)
        assertEquals(5, next.hour)
        assertEquals(23, Duration.between(now, next).toHours())
    }

    @Test fun eveningRequiresLocalChangesAndMorningAlwaysRuns() {
        assertFalse(AutoSyncSlot.EVENING.shouldSync(false))
        assertTrue(AutoSyncSlot.EVENING.shouldSync(true))
        assertTrue(AutoSyncSlot.MORNING.shouldSync(false))
        assertTrue(AutoSyncSlot.MORNING.shouldSync(true))
    }
}
