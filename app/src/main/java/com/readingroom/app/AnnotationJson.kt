package com.readingroom.app

import org.json.JSONArray
import org.json.JSONObject

/** JSON form of an [Annotation], shared by the on-phone store and the backup file. */
object AnnotationJson {
    fun write(a: Annotation): JSONObject = JSONObject().apply {
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
    }

    /** Null when the entry is damaged, so one bad mark never loses the rest. */
    fun read(a: JSONObject): Annotation? = try {
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

    fun writeAll(list: List<Annotation>): JSONArray = JSONArray().also { arr -> list.forEach { arr.put(write(it)) } }

    fun readAll(arr: JSONArray?): List<Annotation> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { read(arr.getJSONObject(it)) }
}
