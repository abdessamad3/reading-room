package com.readingroom.app

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class ReaderTheme { PAPER, SEPIA, NIGHT }

data class Book(
    val id: String,
    val title: String,
    val uri: String,
    val pages: Int,
    val lastPage: Int = 0,
    val bookmarks: List<Int> = emptyList(),
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
) {
    val hasPin: Boolean get() = pinHash != null && pinSalt != null
}

/** Everything the app remembers. Reading time is seconds per ISO date (yyyy-MM-dd). */
data class AppState(
    val books: List<Book> = emptyList(),
    val settings: Settings = Settings(),
    val readingLog: Map<String, Int> = emptyMap(),
)

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
