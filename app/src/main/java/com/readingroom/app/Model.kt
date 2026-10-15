package com.readingroom.app

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class ReaderTheme { PAPER, SEPIA, NIGHT }

enum class AnnotationKind { HIGHLIGHT, NOTE }

enum class MarkColor(val label: String, val argb: Long) {
    YELLOW("Yellow", 0xFFFFD83A),
    GREEN("Green", 0xFF6FD186),
    BLUE("Blue", 0xFF6FB1FF),
    PINK("Pink", 0xFFFF8FBF),
}

/**
 * A highlight or a sticky note on one page. Positions are fractions of the page (0..1) so they
 * stay put at any zoom. A highlight covers left..right and top..bottom; a note sits at left,top.
 */
data class Annotation(
    val id: String,
    val page: Int,
    val kind: AnnotationKind,
    val color: MarkColor,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val text: String = "",
    val createdAt: Long = 0L,
    /** For a highlight made from selected text: one box per line. Empty for a hand-drawn one. */
    val rects: List<Rect4> = emptyList(),
    /** For a highlight made from selected text: the words under it. */
    val quote: String = "",
) {
    /** The areas a highlight covers: its per-line boxes, or the single box it was drawn with. */
    val boxes: List<Rect4> get() = rects.ifEmpty { listOf(Rect4(left, top, right, bottom)) }

    /** True when the point (x, y) touches this mark. [markerW] and [markerH] are the note icon size. */
    fun contains(
        x: Float,
        y: Float,
        markerW: Float,
        markerH: Float,
        slopX: Float = HIT_SLOP,
        slopY: Float = HIT_SLOP,
    ): Boolean = when (kind) {
        AnnotationKind.HIGHLIGHT -> boxes.any { it.contains(x, y, slopX, slopY) }
        AnnotationKind.NOTE -> x in left..(left + markerW) && y in top..(top + markerH)
    }

    private companion object {
        const val HIT_SLOP = 0.01f
    }
}

object NotesExport {
    /** Plain text of every note and highlight, in reading order, for copying out of the app. */
    fun format(title: String, annotations: List<Annotation>): String {
        if (annotations.isEmpty()) return "$title\n\nNo notes or highlights yet."
        val body = annotations
            .sortedWith(compareBy({ it.page }, { it.top }, { it.left }))
            .joinToString("\n\n") { a ->
                val label = when (a.kind) {
                    AnnotationKind.NOTE -> "note"
                    AnnotationKind.HIGHLIGHT -> "highlight, ${a.color.label.lowercase()}"
                }
                val header = "Page ${a.page + 1} ($label)"
                listOfNotNull(
                    header,
                    a.quote.trim().takeIf { it.isNotEmpty() }?.let { "\u201C$it\u201D" },
                    a.text.trim().takeIf { it.isNotEmpty() },
                ).joinToString("\n")
            }
        return "$title\nNotes and highlights\n\n$body"
    }
}

data class Book(
    val id: String,
    val title: String,
    val uri: String,
    val pages: Int,
    val lastPage: Int = 0,
    val bookmarks: List<Int> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
    val addedAt: Long = 0L,
    val lastOpenedAt: Long = 0L,
) {
    /** 0f..1f share of the book already reached. */
    val progress: Float
        get() = if (pages <= 1) 0f else (lastPage.coerceIn(0, pages - 1) + 1f) / pages
}

data class Settings(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val lockEnabled: Boolean = false,
    val goalMinutes: Int = 30,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val zoom: Float = 1f,
    val keepAwake: Boolean = true,
    /** Screen brightness 0.05..1, or [BRIGHTNESS_SYSTEM] to follow the phone. */
    val brightness: Float = BRIGHTNESS_SYSTEM,
    /** When the last backup file was saved (milliseconds), or 0 if never. */
    val lastBackupAt: Long = 0L,
) {
    val hasPin: Boolean get() = pinHash != null && pinSalt != null
}

/** Everything the app remembers. Reading time is seconds per ISO date (yyyy-MM-dd). */
data class AppState(
    val books: List<Book> = emptyList(),
    val settings: Settings = Settings(),
    val readingLog: Map<String, Int> = emptyMap(),
    /** Notes restored from a backup for books that are not on this phone yet. */
    val pending: List<BackupBook> = emptyList(),
)

const val BRIGHTNESS_SYSTEM = -1f

object PinHasher {
    private const val ITERATIONS = 20_000

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.hexToBytes(), ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return key.toHex()
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

object ReadingStats {
    /** A day only counts toward the streak once it holds at least this much reading. */
    const val MIN_STREAK_SECONDS = 60

    fun secondsOn(log: Map<String, Int>, day: LocalDate): Int = log[day.toString()] ?: 0

    fun minutesOn(log: Map<String, Int>, day: LocalDate): Int = secondsOn(log, day) / 60

    /**
     * Consecutive days with reading, counted back from [today]. Today does not break the
     * streak while it is still empty, so the streak reads correctly in the morning.
     */
    fun streak(log: Map<String, Int>, today: LocalDate): Int {
        var day = if (secondsOn(log, today) >= MIN_STREAK_SECONDS) today else today.minusDays(1)
        var count = 0
        while (secondsOn(log, day) >= MIN_STREAK_SECONDS) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
