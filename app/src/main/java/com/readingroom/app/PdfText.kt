package com.readingroom.app

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * The real text of a PDF, page by page, with the position of every word. The page picture still
 * comes from [PdfDoc]; this only answers "which words are where". Pages without text (scans)
 * give an empty list.
 */
class PdfText private constructor(private val document: PDDocument) {
    private val mutex = Mutex()
    private val cache = LruCache<Int, List<Word>>(40)
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var closed = false

    suspend fun words(page: Int): List<Word> = withContext(Dispatchers.Default) {
        cache.get(page) ?: mutex.withLock {
            cache.get(page) ?: if (closed) emptyList() else extract(page).also { cache.put(page, it) }
        }
    }

    private fun extract(page: Int): List<Word> {
        val out = ArrayList<Word>()
        return try {
            val stripper = object : PDFTextStripper() {
                // PDFBox calls this once per word, with the position of each letter.
                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    val letters = textPositions ?: return
                    val word = text?.trim().orEmpty()
                    if (word.isEmpty() || letters.isEmpty()) return
                    val pageWidth = letters[0].pageWidth
                    val pageHeight = letters[0].pageHeight
                    if (pageWidth <= 0f || pageHeight <= 0f) return
                    var left = Float.MAX_VALUE
                    var top = Float.MAX_VALUE
                    var right = -Float.MAX_VALUE
                    var baseline = -Float.MAX_VALUE
                    for (p in letters) {
                        left = min(left, p.xDirAdj)
                        right = max(right, p.xDirAdj + p.widthDirAdj)
                        top = min(top, p.yDirAdj - p.heightDir)
                        baseline = max(baseline, p.yDirAdj)
                    }
                    val bottom = baseline + (baseline - top) * 0.18f
                    out.add(
                        Word(
                            text = word,
                            left = (left / pageWidth).coerceIn(0f, 1f),
                            top = (top / pageHeight).coerceIn(0f, 1f),
                            right = (right / pageWidth).coerceIn(0f, 1f),
                            bottom = (bottom / pageHeight).coerceIn(0f, 1f),
                        ),
                    )
                }
            }
            stripper.setStartPage(page + 1)
            stripper.setEndPage(page + 1)
            stripper.getText(document)
            TextOps.markParagraphs(out)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Closes after any extraction in progress has finished. */
    fun close() {
        closeScope.launch {
            mutex.withLock {
                closed = true
                runCatching { document.close() }
            }
        }
    }

    companion object {
        /** Returns null when the file can't be read, is locked with a password, or forbids copying. */
        fun open(context: Context, uri: Uri): PdfText? {
            return try {
                PDFBoxResourceLoader.init(context.applicationContext)
                val input = context.contentResolver.openInputStream(uri) ?: return null
                val doc = input.use { PDDocument.load(it, MemoryUsageSetting.setupMixed(32L * 1024 * 1024)) }
                if (doc.currentAccessPermission.canExtractContent()) PdfText(doc) else {
                    doc.close()
                    null
                }
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }
    }
}
