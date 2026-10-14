package com.readingroom.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One open PDF. PdfRenderer allows a single open page at a time, so renders are serialized. */
class PdfDoc private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    val pageCount: Int = renderer.pageCount

    private val mutex = Mutex()
    private val cache = LruCache<String, Bitmap>(6)
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var closed = false

    suspend fun render(index: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        val key = "$index@$widthPx"
        cache.get(key) ?: mutex.withLock {
            if (closed) throw CancellationException("PDF closed")
            cache.get(key) ?: renderer.openPage(index).use { page ->
                val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(key, bitmap)
                bitmap
            }
        }
    }

    /** Closes after any render in progress has finished. */
    fun close() {
        closeScope.launch {
            mutex.withLock {
                closed = true
                runCatching { renderer.close() }
                runCatching { pfd.close() }
            }
        }
    }

    companion object {
        /** Returns null when the file is missing, encrypted or not a valid PDF. */
        fun open(context: Context, uri: Uri): PdfDoc? = try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) null else try {
                PdfDoc(pfd, PdfRenderer(pfd))
            } catch (e: Exception) {
                pfd.close()
                null
            }
        } catch (e: Exception) {
            null
        }

        suspend fun thumbnail(context: Context, uri: Uri, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
            val doc = open(context, uri) ?: return@withContext null
            try {
                if (doc.pageCount == 0) null else doc.render(0, widthPx)
            } finally {
                doc.close()
            }
        }
    }
}
