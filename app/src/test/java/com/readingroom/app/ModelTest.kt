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

class AnnotationTest {
    private fun mark(kind: AnnotationKind, l: Float, t: Float, r: Float, b: Float, text: String = "", page: Int = 0, color: MarkColor = MarkColor.YELLOW) =
        Annotation("id", page, kind, color, l, t, r, b, text)

    @Test
    fun highlightIsHitInsideAndJustOutsideItsEdges() {
        val h = mark(AnnotationKind.HIGHLIGHT, 0.2f, 0.3f, 0.6f, 0.35f)
        assertTrue(h.contains(0.4f, 0.32f, 0.07f, 0.05f))
        assertTrue(h.contains(0.195f, 0.32f, 0.07f, 0.05f))
        assertFalse(h.contains(0.1f, 0.32f, 0.07f, 0.05f))
        assertFalse(h.contains(0.4f, 0.5f, 0.07f, 0.05f))
    }

    @Test
    fun thinHighlightIsEasyToTapWithAFingerSizedMargin() {
        val thin = mark(AnnotationKind.HIGHLIGHT, 0.2f, 0.300f, 0.6f, 0.303f)
        // 0.03 of the page height away is a miss with the default margin but a hit with a wider one.
        assertFalse(thin.contains(0.4f, 0.33f, 0.07f, 0.05f))
        assertTrue(thin.contains(0.4f, 0.33f, 0.07f, 0.05f, slopX = 0.03f, slopY = 0.03f))
    }

    @Test
    fun noteIsHitOnlyOnItsIcon() {
        val n = mark(AnnotationKind.NOTE, 0.5f, 0.5f, 0.5f, 0.5f)
        assertTrue(n.contains(0.52f, 0.52f, 0.07f, 0.05f))
        assertFalse(n.contains(0.49f, 0.52f, 0.07f, 0.05f))
        assertFalse(n.contains(0.52f, 0.6f, 0.07f, 0.05f))
    }

    @Test
    fun exportListsMarksInReadingOrder() {
        val text = NotesExport.format(
            "Walden",
            listOf(
                mark(AnnotationKind.NOTE, 0.1f, 0.5f, 0.1f, 0.5f, text = "Compare with chapter 2", page = 11),
                mark(AnnotationKind.HIGHLIGHT, 0.1f, 0.2f, 0.9f, 0.25f, page = 2, color = MarkColor.GREEN),
                mark(AnnotationKind.HIGHLIGHT, 0.1f, 0.6f, 0.9f, 0.65f, text = "Key idea", page = 2),
            ),
        )
        val expected = listOf(
            "Walden",
            "Notes and highlights",
            "",
            "Page 3 (highlight, green)",
            "",
            "Page 3 (highlight, yellow)",
            "Key idea",
            "",
            "Page 12 (note)",
            "Compare with chapter 2",
        ).joinToString("\n")
        assertEquals(expected, text)
    }

    @Test
    fun exportOfNothingSaysSo() {
        assertEquals("Walden\n\nNo notes or highlights yet.", NotesExport.format("Walden", emptyList()))
    }
}
