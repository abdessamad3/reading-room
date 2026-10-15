package com.readingroom.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * One book's notes and place, kept apart from the phone's file addresses. A book is recognised on
 * another phone by its name and page count ([BackupCodec.key]).
 */
data class BackupBook(
    val key: String,
    val title: String,
    val pages: Int,
    val lastPage: Int,
    val bookmarks: List<Int>,
    val annotations: List<Annotation>,
)

data class Backup(
    val exportedAt: Long,
    val books: List<BackupBook>,
    val readingLog: Map<String, Int>,
    val goalMinutes: Int,
    val theme: ReaderTheme,
    val zoom: Float,
    val keepAwake: Boolean,
    val brightness: Float,
)

data class RestoreResult(val state: AppState, val matched: Int, val waiting: Int)

/** Turns the app's data into a backup file and back. The PIN and lock settings are never included. */
object BackupCodec {
    private const val APP = "reading-room"
    private const val VERSION = 1

    fun key(title: String, pages: Int): String = "${title.trim().lowercase()}|$pages"

    fun keyOf(book: Book): String = key(book.title, book.pages)

    fun create(state: AppState, now: Long): Backup {
        val onPhone = state.books.map {
            BackupBook(keyOf(it), it.title, it.pages, it.lastPage, it.bookmarks, it.annotations)
        }
        // Books restored from an earlier backup but not imported yet must survive a new backup too.
        val onPhoneKeys = onPhone.map { it.key }.toSet()
        val waiting = state.pending.filter { it.key !in onPhoneKeys }
        val s = state.settings
        return Backup(now, onPhone + waiting, state.readingLog, s.goalMinutes, s.theme, s.zoom, s.keepAwake, s.brightness)
    }

    fun toJson(b: Backup): String = JSONObject().apply {
        put("app", APP)
        put("version", VERSION)
        put("exportedAt", b.exportedAt)
        put("books", JSONArray().also { arr -> b.books.forEach { arr.put(writeBook(it)) } })
        put("readingLog", JSONObject().also { o -> b.readingLog.forEach { (day, sec) -> o.put(day, sec) } })
        put("settings", JSONObject().apply {
            put("goalMinutes", b.goalMinutes)
            put("theme", b.theme.name)
            put("zoom", b.zoom.toDouble())
            put("keepAwake", b.keepAwake)
            put("brightness", b.brightness.toDouble())
        })
    }.toString(2)

    /** Null when the text is not a Reading Room backup, or comes from a newer version of the app. */
    fun fromJson(text: String): Backup? = try {
        val o = JSONObject(text)
        if (o.optString("app") != APP || o.optInt("version", 0) !in 1..VERSION) null else {
            val s = o.optJSONObject("settings")
            val logObj = o.optJSONObject("readingLog")
            Backup(
                exportedAt = o.optLong("exportedAt", 0L),
                books = o.optJSONArray("books")?.let { arr -> (0 until arr.length()).mapNotNull { readBook(arr.getJSONObject(it)) } }
                    ?: emptyList(),
                readingLog = logObj?.keys()?.asSequence()?.associateWith { logObj.getInt(it) } ?: emptyMap(),
                goalMinutes = s?.optInt("goalMinutes", 30) ?: 30,
                theme = runCatching { ReaderTheme.valueOf(s?.optString("theme") ?: "PAPER") }.getOrDefault(ReaderTheme.PAPER),
                zoom = (s?.optDouble("zoom", 1.0) ?: 1.0).toFloat(),
                keepAwake = s?.optBoolean("keepAwake", true) ?: true,
                brightness = (s?.optDouble("brightness", BRIGHTNESS_SYSTEM.toDouble()) ?: BRIGHTNESS_SYSTEM.toDouble()).toFloat(),
            )
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Merges a backup into the phone's data without losing anything already there. Books that are
     * not on this phone yet wait in [AppState.pending] and attach when the PDF is imported.
     */
    fun restore(state: AppState, backup: Backup): RestoreResult {
        val books = state.books.toMutableList()
        val pending = state.pending.toMutableList()
        var matched = 0
        for (bb in backup.books) {
            val i = books.indexOfFirst { keyOf(it) == bb.key }
            if (i >= 0) {
                books[i] = mergeInto(books[i], bb)
                pending.removeAll { it.key == bb.key }
                matched++
            } else {
                val j = pending.indexOfFirst { it.key == bb.key }
                if (j >= 0) pending[j] = mergePending(pending[j], bb) else pending.add(bb)
            }
        }
        val days = state.readingLog.keys + backup.readingLog.keys
        val log = days.associateWith { max(state.readingLog[it] ?: 0, backup.readingLog[it] ?: 0) }
        val settings = state.settings.copy(
            goalMinutes = backup.goalMinutes,
            theme = backup.theme,
            zoom = backup.zoom,
            keepAwake = backup.keepAwake,
            brightness = backup.brightness,
        )
        val bookKeys = books.map { keyOf(it) }.toSet()
        return RestoreResult(
            state = state.copy(books = books, pending = pending, readingLog = log, settings = settings),
            matched = matched,
            waiting = pending.count { it.key !in bookKeys },
        )
    }

    /** A newly imported book picks up any notes that were waiting for it. */
    fun attachPending(book: Book, pending: List<BackupBook>): Pair<Book, List<BackupBook>> {
        val i = pending.indexOfFirst { it.key == keyOf(book) }
        if (i < 0) return book to pending
        return mergeInto(book, pending[i]) to pending.filterIndexed { index, _ -> index != i }
    }

    private fun mergeInto(book: Book, b: BackupBook): Book {
        val have = book.annotations.map { it.id }.toSet()
        val untouched = book.lastOpenedAt == 0L && book.lastPage == 0
        return book.copy(
            annotations = book.annotations + b.annotations.filter { it.id !in have },
            bookmarks = (book.bookmarks + b.bookmarks).distinct().sorted(),
            lastPage = if (untouched) b.lastPage.coerceIn(0, (book.pages - 1).coerceAtLeast(0)) else book.lastPage,
        )
    }

    private fun mergePending(a: BackupBook, b: BackupBook): BackupBook {
        val have = a.annotations.map { it.id }.toSet()
        return a.copy(
            annotations = a.annotations + b.annotations.filter { it.id !in have },
            bookmarks = (a.bookmarks + b.bookmarks).distinct().sorted(),
            lastPage = b.lastPage,
        )
    }

    fun writeBook(b: BackupBook): JSONObject = JSONObject().apply {
        put("key", b.key)
        put("title", b.title)
        put("pages", b.pages)
        put("lastPage", b.lastPage)
        put("bookmarks", JSONArray(b.bookmarks))
        put("annotations", AnnotationJson.writeAll(b.annotations))
    }

    fun readBook(o: JSONObject): BackupBook? = try {
        val marks = o.optJSONArray("bookmarks")
        BackupBook(
            key = o.getString("key"),
            title = o.getString("title"),
            pages = o.getInt("pages"),
            lastPage = o.optInt("lastPage", 0),
            bookmarks = if (marks == null) emptyList() else (0 until marks.length()).map { marks.getInt(it) },
            annotations = AnnotationJson.readAll(o.optJSONArray("annotations")),
        )
    } catch (e: Exception) {
        null
    }
}
