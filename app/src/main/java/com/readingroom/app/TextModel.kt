package com.readingroom.app

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A box on a page, as fractions of the page width and height (0..1). */
data class Rect4(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float, slopX: Float = 0f, slopY: Float = 0f): Boolean =
        x in (left - slopX)..(right + slopX) && y in (top - slopY)..(bottom + slopY)

    fun union(o: Rect4) = Rect4(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))
}

/** One word of real text on a page, with where it sits. Words are in reading order. */
data class Word(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** True when this word starts a new paragraph or column. */
    val paragraphBreakBefore: Boolean = false,
) {
    val rect: Rect4 get() = Rect4(left, top, right, bottom)
}

/** What the user has selected on one page: words [from]..[to] in reading order. */
data class Selection(val page: Int, val anchor: Int, val head: Int) {
    val from: Int get() = min(anchor, head)
    val to: Int get() = max(anchor, head)
}

object TextOps {
    /**
     * Index of the word closest to the point (x, y), or -1 when none is within [maxDistance]
     * (or the page has no words). A point inside a word has distance 0.
     */
    fun nearestWord(words: List<Word>, x: Float, y: Float, maxDistance: Float = Float.MAX_VALUE): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in words.indices) {
            val w = words[i]
            val dx = when {
                x < w.left -> w.left - x
                x > w.right -> x - w.right
                else -> 0f
            }
            val dy = when {
                y < w.top -> w.top - y
                y > w.bottom -> y - w.bottom
                else -> 0f
            }
            val d = hypot(dx, dy)
            if (d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
        return if (best >= 0 && bestDistance <= maxDistance) best else -1
    }

    /** The selected words as text. Words are joined by spaces and paragraphs by a line break. */
    fun selectionText(words: List<Word>, from: Int, to: Int): String {
        if (words.isEmpty()) return ""
        val a = from.coerceIn(0, words.lastIndex)
        val b = to.coerceIn(a, words.lastIndex)
        val sb = StringBuilder()
        for (i in a..b) {
            if (i > a) sb.append(if (words[i].paragraphBreakBefore) "\n" else " ")
            sb.append(words[i].text)
        }
        return sb.toString()
    }

    /** One box per line of text for words [from]..[to], so a highlight follows the lines. */
    fun rectsFor(words: List<Word>, from: Int, to: Int): List<Rect4> {
        if (words.isEmpty()) return emptyList()
        val a = from.coerceIn(0, words.lastIndex)
        val b = to.coerceIn(a, words.lastIndex)
        val out = ArrayList<Rect4>()
        var current = words[a].rect
        for (i in a + 1..b) {
            val w = words[i].rect
            val sameLine = abs(centerY(w) - centerY(current)) < 0.6f * (current.bottom - current.top)
            if (sameLine) current = current.union(w) else {
                out.add(current)
                current = w
            }
        }
        out.add(current)
        return out
    }

    /** Fills in [Word.paragraphBreakBefore] from the vertical gaps between consecutive words. */
    fun markParagraphs(words: List<Word>): List<Word> {
        if (words.size < 2) return words
        val out = ArrayList<Word>(words.size)
        out.add(words[0])
        for (i in 1 until words.size) {
            val prev = words[i - 1]
            val w = words[i]
            val lineHeight = (prev.bottom - prev.top).coerceAtLeast(0.001f)
            val bigGapDown = w.top > prev.bottom + 0.6f * lineHeight
            val jumpedUp = w.top < prev.top - 2f * lineHeight
            out.add(if (bigGapDown || jumpedUp) w.copy(paragraphBreakBefore = true) else w)
        }
        return out
    }

    /** Word ranges where [query] occurs. Case does not matter; several words match in a row. */
    fun findMatches(words: List<Word>, query: String): List<IntRange> {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || words.isEmpty()) return emptyList()
        val lower = words.map { it.text.lowercase() }
        val out = ArrayList<IntRange>()
        var i = 0
        while (i <= lower.size - tokens.size) {
            if (tokens.indices.all { k -> lower[i + k].contains(tokens[k]) }) {
                out.add(i..i + tokens.size - 1)
                i += tokens.size
            } else {
                i++
            }
        }
        return out
    }

    /** A short run of text around a match, for the search results list. */
    fun snippet(words: List<Word>, range: IntRange, before: Int = 6, after: Int = 8): String {
        if (words.isEmpty()) return ""
        val start = max(0, range.first - before)
        val end = min(words.lastIndex, range.last + after)
        val body = (start..end).joinToString(" ") { words[it].text }
        return (if (start > 0) "… " else "") + body + (if (end < words.lastIndex) " …" else "")
    }

    private fun centerY(r: Rect4) = (r.top + r.bottom) / 2f
}
