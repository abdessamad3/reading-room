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
                    put("annotations", JSONArray().also { list ->
                        b.annotations.forEach { a ->
                            list.put(JSONObject().apply {
                                put("id", a.id)
                                put("page", a.page)
                                put("kind", a.kind.name)
                                put("color", a.color.name)
                                put("left", a.left.toDouble())
                                put("top", a.top.toDouble())
                                put("right", a.right.toDouble())
                                put("bottom", a.bottom.toDouble())
                                put("text", a.text)
                                put("createdAt", a.createdAt)
                                put("quote", a.quote)
                                put("rects", JSONArray().also { rs ->
                                    a.rects.forEach { r ->
                                        rs.put(JSONArray(listOf(r.left.toDouble(), r.top.toDouble(), r.right.toDouble(), r.bottom.toDouble())))
                                    }
                                })
                            })
                        }
                    })
                    put("addedAt", b.addedAt)
                    put("lastOpenedAt", b.lastOpenedAt)
                })
            }
        })
        put("settings", JSONObject().apply {
            put("pinHash", s.settings.pinHash ?: JSONObject.NULL)
            put("pinSalt", s.settings.pinSalt ?: JSONObject.NULL)
            put("lockEnabled", s.settings.lockEnabled)
            put("goalMinutes", s.settings.goalMinutes)
            put("theme", s.settings.theme.name)
            put("zoom", s.settings.zoom.toDouble())
            put("keepAwake", s.settings.keepAwake)
            put("brightness", s.settings.brightness.toDouble())
        })
        put("log", JSONObject().also { o -> s.readingLog.forEach { (day, sec) -> o.put(day, sec) } })
    }

    private fun parse(o: JSONObject): AppState {
        val books = o.optJSONArray("books")?.let { arr ->
            (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                val marks = b.optJSONArray("bookmarks")
                val notes = b.optJSONArray("annotations")
                Book(
                    id = b.getString("id"),
                    title = b.getString("title"),
                    uri = b.getString("uri"),
                    pages = b.getInt("pages"),
                    lastPage = b.optInt("lastPage", 0),
                    bookmarks = if (marks == null) emptyList() else (0 until marks.length()).map { marks.getInt(it) },
                    annotations = if (notes == null) emptyList() else (0 until notes.length()).mapNotNull { j ->
                        parseAnnotation(notes.getJSONObject(j))
                    },
                    addedAt = b.optLong("addedAt", 0L),
                    lastOpenedAt = b.optLong("lastOpenedAt", 0L),
                )
            }
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
        )
        val logObj = o.optJSONObject("log")
        val log = logObj?.keys()?.asSequence()?.associateWith { logObj.getInt(it) } ?: emptyMap()
        return AppState(books, settings, log)
    }

    private fun parseAnnotation(a: JSONObject): Annotation? = try {
        Annotation(
            id = a.getString("id"),
            page = a.getInt("page"),
            kind = AnnotationKind.valueOf(a.getString("kind")),
            color = MarkColor.valueOf(a.getString("color")),
            left = a.getDouble("left").toFloat(),
            top = a.getDouble("top").toFloat(),
            right = a.getDouble("right").toFloat(),
            bottom = a.getDouble("bottom").toFloat(),
            text = a.optString("text", ""),
            createdAt = a.optLong("createdAt", 0L),
            quote = a.optString("quote", ""),
            rects = a.optJSONArray("rects")?.let { rs ->
                (0 until rs.length()).map { i ->
                    val r = rs.getJSONArray(i)
                    Rect4(r.getDouble(0).toFloat(), r.getDouble(1).toFloat(), r.getDouble(2).toFloat(), r.getDouble(3).toFloat())
                }
            } ?: emptyList(),
        )
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val KEY = "state"
    }
}
