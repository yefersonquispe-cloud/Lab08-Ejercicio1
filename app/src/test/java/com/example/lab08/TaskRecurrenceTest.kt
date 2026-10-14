package com.example.lab08

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class TaskRecurrenceTest {
    @Test fun dailyKeepsLocalHourAcrossDaylightSaving() {
        val base = ZonedDateTime.parse("2026-03-07T09:00:00-05:00[America/New_York]").toInstant().toEpochMilli()
        val expected = ZonedDateTime.parse("2026-03-08T09:00:00-04:00[America/New_York]").toInstant().toEpochMilli()
        assertEquals(expected, nextRepeatDate(base, TaskRepeat.DAILY, base, "America/New_York"))
        assertEquals(23 * 3_600_000L, expected - base)
    }
    @Test fun weeklyKeepsScheduleAndSkipsMissedCycles() {
        val base = ZonedDateTime.parse("2026-10-01T08:00:00-05:00[America/Lima]").toInstant().toEpochMilli()
        val now = ZonedDateTime.parse("2026-10-18T12:00:00-05:00[America/Lima]").toInstant().toEpochMilli()
        val expected = ZonedDateTime.parse("2026-10-22T08:00:00-05:00[America/Lima]").toInstant().toEpochMilli()
        assertEquals(expected, nextRepeatDate(base, TaskRepeat.WEEKLY, now, "America/Lima"))
        assertNull(nextRepeatDate(base, TaskRepeat.NONE, now, "America/Lima"))
    }
}
