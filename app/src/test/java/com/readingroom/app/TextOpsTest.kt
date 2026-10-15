package com.readingroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOpsTest {
    /** Two lines of three words: "The quick brown" and "fox jumps over". */
    private val words = listOf(
        Word("The", 0.10f, 0.10f, 0.20f, 0.13f),
        Word("quick", 0.22f, 0.10f, 0.35f, 0.13f),
        Word("brown", 0.37f, 0.10f, 0.50f, 0.13f),
        Word("fox", 0.10f, 0.14f, 0.18f, 0.17f),
        Word("jumps", 0.20f, 0.14f, 0.33f, 0.17f),
        Word("over", 0.35f, 0.14f, 0.45f, 0.17f),
    )

    @Test
    fun nearestWordFindsTheWordUnderTheFinger() {
        assertEquals(1, TextOps.nearestWord(words, 0.28f, 0.115f))
        assertEquals(4, TextOps.nearestWord(words, 0.25f, 0.155f))
    }

    @Test
    fun nearestWordSnapsToTheClosestWordBetweenLines() {
        // A touch in the gap just under "brown" belongs to "brown", not to the line below.
        assertEquals(2, TextOps.nearestWord(words, 0.45f, 0.133f))
    }

    @Test
    fun nearestWordGivesUpWhenTooFarAway() {
        assertEquals(-1, TextOps.nearestWord(words, 0.9f, 0.9f, maxDistance = 0.06f))
        assertEquals(-1, TextOps.nearestWord(emptyList(), 0.5f, 0.5f))
    }

    @Test
    fun selectionTextJoinsWordsWithSpaces() {
        assertEquals("quick brown fox", TextOps.selectionText(words, 1, 3))
        assertEquals("brown", TextOps.selectionText(words, 2, 2))
    }

    @Test
    fun selectionTextToleratesOutOfRangeIndexes() {
        assertEquals("The quick brown fox jumps over", TextOps.selectionText(words, -5, 99))
        assertEquals("", TextOps.selectionText(emptyList(), 0, 3))
    }

    @Test
    fun selectionTextBreaksTheLineAtAParagraph() {
        val marked = words.mapIndexed { i, w -> if (i == 3) w.copy(paragraphBreakBefore = true) else w }
        assertEquals("brown\nfox", TextOps.selectionText(marked, 2, 3))
    }

    @Test
    fun rectsForGivesOneBoxPerLine() {
        val rects = TextOps.rectsFor(words, 1, 4)
        assertEquals(2, rects.size)
        assertEquals(Rect4(0.22f, 0.10f, 0.50f, 0.13f), rects[0])
        assertEquals(Rect4(0.10f, 0.14f, 0.33f, 0.17f), rects[1])
    }

    @Test
    fun rectsForOneLineIsOneBox() {
        assertEquals(listOf(Rect4(0.10f, 0.10f, 0.50f, 0.13f)), TextOps.rectsFor(words, 0, 2))
    }

    @Test
    fun markParagraphsFlagsABigGapButNotNormalLineSpacing() {
        val spaced = words + Word("New", 0.10f, 0.25f, 0.20f, 0.28f)
        val marked = TextOps.markParagraphs(spaced)
        assertFalse(marked[3].paragraphBreakBefore)
        assertTrue(marked[6].paragraphBreakBefore)
    }

    @Test
    fun findMatchesIgnoresCaseAndFindsPartialWords() {
        assertEquals(listOf(1..1), TextOps.findMatches(words, "QUICK"))
        assertEquals(listOf(4..4), TextOps.findMatches(words, "jump"))
    }

    @Test
    fun findMatchesFindsAPhraseAcrossWords() {
        assertEquals(listOf(1..2), TextOps.findMatches(words, "quick brown"))
        assertEquals(emptyList<IntRange>(), TextOps.findMatches(words, "brown quick"))
    }

    @Test
    fun findMatchesOfNothingIsEmpty() {
        assertEquals(emptyList<IntRange>(), TextOps.findMatches(words, "   "))
        assertEquals(emptyList<IntRange>(), TextOps.findMatches(emptyList(), "fox"))
    }

    @Test
    fun snippetShowsContextAndEllipses() {
        val long = (1..20).map { Word("w$it", 0f, 0f, 0f, 0f) }
        assertEquals("… w5 w6 w7 w8 w9 w10 w11 w12 w13 w14 w15 w16 …", TextOps.snippet(long, 10..10 + 0, before = 5, after = 5))
        assertEquals("w1 w2 w3 w4 …", TextOps.snippet(long, 0..0, before = 5, after = 3))
    }

    @Test
    fun selectionOrdersItsEndsWhateverTheDragDirection() {
        val s = Selection(page = 2, anchor = 5, head = 1)
        assertEquals(1, s.from)
        assertEquals(5, s.to)
    }
}

class AnnotationTextTest {
    private fun highlight(rects: List<Rect4>, quote: String = "") = Annotation(
        id = "a", page = 0, kind = AnnotationKind.HIGHLIGHT, color = MarkColor.YELLOW,
        left = rects.minOf { it.left }, top = rects.minOf { it.top },
        right = rects.maxOf { it.right }, bottom = rects.maxOf { it.bottom },
        rects = rects, quote = quote,
    )

    @Test
    fun multiLineHighlightIsHitOnItsLinesButNotInTheGapBesideThem() {
        val h = highlight(listOf(Rect4(0.5f, 0.10f, 0.9f, 0.13f), Rect4(0.1f, 0.14f, 0.3f, 0.17f)))
        assertTrue(h.contains(0.7f, 0.115f, 0.07f, 0.05f, slopX = 0f, slopY = 0f))
        assertTrue(h.contains(0.2f, 0.155f, 0.07f, 0.05f, slopX = 0f, slopY = 0f))
        // Inside the bounding box but on neither line.
        assertFalse(h.contains(0.2f, 0.115f, 0.07f, 0.05f, slopX = 0f, slopY = 0f))
    }

    @Test
    fun drawnHighlightWithoutRectsUsesItsOwnBox() {
        val h = Annotation("a", 0, AnnotationKind.HIGHLIGHT, MarkColor.GREEN, 0.2f, 0.3f, 0.6f, 0.35f)
        assertEquals(listOf(Rect4(0.2f, 0.3f, 0.6f, 0.35f)), h.boxes)
    }

    @Test
    fun exportQuotesTheHighlightedWordsBeforeTheNote() {
        val h = highlight(listOf(Rect4(0.1f, 0.1f, 0.5f, 0.13f)), quote = "The quick brown fox").copy(text = "Check this")
        assertEquals(
            "Walden\nNotes and highlights\n\nPage 1 (highlight, yellow)\n“The quick brown fox”\nCheck this",
            NotesExport.format("Walden", listOf(h)),
        )
    }
}
