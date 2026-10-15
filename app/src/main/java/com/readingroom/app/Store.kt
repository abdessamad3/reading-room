package com.readingroom.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Keeps the whole [AppState] as one JSON document in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("reading_room", Context.MODE_PRIVATE)

    fun load(): AppState = try {
        prefs.getString(KEY, null)?.let { parse(JSONObject(it)) } ?: AppState()
    } catch (e: Exception) {
        AppState()
    }

    fun save(state: AppState) {
        prefs.edit().putString(KEY, write(state).toString()).apply()
    }

    private fun write(s: AppState) = JSONObject().apply {
        put("books", JSONArray().also { arr ->
            s.books.forEach { b ->
                arr.put(JSONObject().apply {
                    put("id", b.id)
                    put("title", b.title)
                    put("uri", b.uri)
                    put("pages", b.pages)
                    put("lastPage", b.lastPage)
                    put("bookmarks", JSONArray(b.bookmarks))
                    put("annotations", AnnotationJson.writeAll(b.annotations))
                    put("addedAt", b.addedAt)
                    put("lastOpenedAt", b.lastOpenedAt)
                })
            }
        })
        put("pending", JSONArray().also { arr -> s.pending.forEach { arr.put(BackupCodec.writeBook(it)) } })
        put("settings", JSONObject().apply {
            put("pinHash", s.settings.pinHash ?: JSONObject.NULL)
            put("pinSalt", s.settings.pinSalt ?: JSONObject.NULL)
            put("lockEnabled", s.settings.lockEnabled)
            put("goalMinutes", s.settings.goalMinutes)
            put("theme", s.settings.theme.name)
            put("zoom", s.settings.zoom.toDouble())
            put("keepAwake", s.settings.keepAwake)
            put("brightness", s.settings.brightness.toDouble())
            put("lastBackupAt", s.settings.lastBackupAt)
        })
        put("log", JSONObject().also { o -> s.readingLog.forEach { (day, sec) -> o.put(day, sec) } })
    }

    private fun parse(o: JSONObject): AppState {
        val books = o.optJSONArray("books")?.let { arr ->
            (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                val marks = b.optJSONArray("bookmarks")
                Book(
                    id = b.getString("id"),
                    title = b.getString("title"),
                    uri = b.getString("uri"),
                    pages = b.getInt("pages"),
                    lastPage = b.optInt("lastPage", 0),
                    bookmarks = if (marks == null) emptyList() else (0 until marks.length()).map { marks.getInt(it) },
                    annotations = AnnotationJson.readAll(b.optJSONArray("annotations")),
                    addedAt = b.optLong("addedAt", 0L),
                    lastOpenedAt = b.optLong("lastOpenedAt", 0L),
                )
            }
        } ?: emptyList()
        val pending = o.optJSONArray("pending")?.let { arr ->
            (0 until arr.length()).mapNotNull { BackupCodec.readBook(arr.getJSONObject(it)) }
        } ?: emptyList()
        val s = o.optJSONObject("settings")
        val settings = if (s == null) Settings() else Settings(
            pinHash = s.optString("pinHash").takeIf { it.isNotEmpty() && !s.isNull("pinHash") },
            pinSalt = s.optString("pinSalt").takeIf { it.isNotEmpty() && !s.isNull("pinSalt") },
            lockEnabled = s.optBoolean("lockEnabled", false),
            goalMinutes = s.optInt("goalMinutes", 30),
            theme = runCatching { ReaderTheme.valueOf(s.optString("theme", "PAPER")) }.getOrDefault(ReaderTheme.PAPER),
            zoom = s.optDouble("zoom", 1.0).toFloat(),
            keepAwake = s.optBoolean("keepAwake", true),
            brightness = s.optDouble("brightness", BRIGHTNESS_SYSTEM.toDouble()).toFloat(),
            lastBackupAt = s.optLong("lastBackupAt", 0L),
        )
        val logObj = o.optJSONObject("log")
        val log = logObj?.keys()?.asSequence()?.associateWith { logObj.getInt(it) } ?: emptyMap()
        return AppState(books, settings, log, pending)
    }

    private companion object {
        const val KEY = "state"
    }
}
