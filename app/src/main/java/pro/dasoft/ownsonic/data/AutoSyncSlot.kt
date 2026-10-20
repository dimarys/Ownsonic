package pro.dasoft.ownsonic.data

import java.time.ZonedDateTime

enum class AutoSyncSlot(val hour: Int, val minute: Int) {
    EVENING(23, 30), MORNING(5, 0);

    fun nextRun(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(hour, minute).atZone(now.zone)
    }

    fun shouldSync(hasLocalChanges: Boolean): Boolean = this == MORNING || hasLocalChanges
}
