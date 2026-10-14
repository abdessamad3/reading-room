package com.readingroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PinHasherTest {
    @Test
    fun correctPinVerifies() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("2468", salt)
        assertTrue(PinHasher.verify("2468", salt, hash))
    }

    @Test
    fun wrongPinFails() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("2468", salt)
        assertFalse(PinHasher.verify("2469", salt, hash))
    }

    @Test
    fun sameSaltAndPinGiveSameHash_differentSaltDoesNot() {
        val a = PinHasher.newSalt()
        val b = PinHasher.newSalt()
        assertEquals(PinHasher.hash("1111", a), PinHasher.hash("1111", a))
        assertNotEquals(PinHasher.hash("1111", a), PinHasher.hash("1111", b))
    }

    @Test
    fun hashDoesNotContainThePin() {
        assertFalse(PinHasher.hash("2468", PinHasher.newSalt()).contains("2468"))
    }
}

class ReadingStatsTest {
    private val today = LocalDate.of(2026, 10, 14)

    private fun log(vararg daysAgo: Pair<Int, Int>) =
        daysAgo.associate { (ago, seconds) -> today.minusDays(ago.toLong()).toString() to seconds }

    @Test
    fun minutesRoundDown() {
        assertEquals(1, ReadingStats.minutesOn(log(0 to 119), today))
        assertEquals(0, ReadingStats.minutesOn(emptyMap(), today))
    }

    @Test
    fun streakCountsConsecutiveDaysIncludingToday() {
        assertEquals(3, ReadingStats.streak(log(0 to 600, 1 to 300, 2 to 90, 4 to 600), today))
    }

    @Test
    fun emptyTodayDoesNotBreakTheStreak() {
        assertEquals(2, ReadingStats.streak(log(1 to 300, 2 to 300), today))
    }

    @Test
    fun missedYesterdayEndsTheStreak() {
        assertEquals(0, ReadingStats.streak(log(2 to 300, 3 to 300), today))
        assertEquals(1, ReadingStats.streak(log(0 to 300, 2 to 300), today))
    }

    @Test
    fun brieflyOpenedDaysDoNotCount() {
        assertEquals(0, ReadingStats.streak(log(0 to 30, 1 to 59), today))
    }
}

class BookTest {
    private fun book(pages: Int, last: Int) = Book("id", "t", "u", pages, lastPage = last)

    @Test
    fun progressReachesOneOnTheLastPage() {
        assertEquals(1f, book(10, 9).progress, 0.001f)
        assertEquals(0.1f, book(10, 0).progress, 0.001f)
    }

    @Test
    fun singlePageBookHasNoProgress() {
        assertEquals(0f, book(1, 0).progress, 0.001f)
    }
}
