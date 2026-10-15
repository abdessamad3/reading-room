package com.readingroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private fun note(id: String, page: Int = 2, text: String = "n") =
        Annotation(id, page, AnnotationKind.NOTE, MarkColor.BLUE, 0.1f, 0.2f, 0.1f, 0.2f, text = text, createdAt = 5L)

    private fun mark(id: String, quote: String) = Annotation(
        id, 4, AnnotationKind.HIGHLIGHT, MarkColor.GREEN, 0.1f, 0.2f, 0.9f, 0.4f, quote = quote,
        rects = listOf(Rect4(0.5f, 0.2f, 0.9f, 0.25f), Rect4(0.1f, 0.3f, 0.6f, 0.4f)),
    )

    private fun book(title: String, pages: Int, uri: String, vararg notes: Annotation, last: Int = 0, opened: Long = 0L, marks: List<Int> = emptyList()) =
        Book(id = "id-$title", title = title, uri = uri, pages = pages, lastPage = last, bookmarks = marks,
            annotations = notes.toList(), lastOpenedAt = opened)

    @Test
    fun backupSurvivesAJsonRoundTrip() {
        val state = AppState(
            books = listOf(book("Walden", 320, "content://old/1", note("a"), mark("b", "The quick brown fox"), last = 154, marks = listOf(3, 9))),
            settings = Settings(goalMinutes = 45, theme = ReaderTheme.NIGHT, zoom = 1.5f, keepAwake = false, brightness = 0.4f),
            readingLog = mapOf("2026-10-14" to 1800),
        )
        val parsed = BackupCodec.fromJson(BackupCodec.toJson(BackupCodec.create(state, now = 99L)))
        assertNotNull(parsed)
        parsed!!
        assertEquals(99L, parsed.exportedAt)
        assertEquals(1, parsed.books.size)
        val b = parsed.books[0]
        assertEquals("walden|320", b.key)
        assertEquals(154, b.lastPage)
        assertEquals(listOf(3, 9), b.bookmarks)
        assertEquals(state.books[0].annotations, b.annotations)
        assertEquals(mapOf("2026-10-14" to 1800), parsed.readingLog)
        assertEquals(45, parsed.goalMinutes)
        assertEquals(ReaderTheme.NIGHT, parsed.theme)
        assertEquals(0.4f, parsed.brightness, 0.0001f)
    }

    @Test
    fun backupNeverContainsThePinOrLockState() {
        val state = AppState(settings = Settings(pinHash = "secret-hash", pinSalt = "secret-salt", lockEnabled = true))
        val json = BackupCodec.toJson(BackupCodec.create(state, 1L))
        assertFalse(json.contains("secret-hash"))
        assertFalse(json.contains("secret-salt"))
        assertFalse(json.contains("lockEnabled"))
    }

    @Test
    fun notABackupFileIsRejected() {
        assertNull(BackupCodec.fromJson("not json at all"))
        assertNull(BackupCodec.fromJson("""{"hello":"world"}"""))
        assertNull(BackupCodec.fromJson("""{"app":"reading-room","version":99}"""))
    }

    @Test
    fun restoreMergesNotesIntoTheSameBookOnAnotherPhone() {
        val backupState = AppState(books = listOf(book("Walden", 320, "content://old/1", note("a"), note("b"), last = 200, marks = listOf(5))))
        val backup = BackupCodec.create(backupState, 1L)

        val newPhone = AppState(books = listOf(book("Walden", 320, "content://new/9", note("b"), note("c"), marks = listOf(7))))
        val result = BackupCodec.restore(newPhone, backup)

        val merged = result.state.books.single()
        assertEquals(listOf("b", "c", "a"), merged.annotations.map { it.id })
        assertEquals(listOf(5, 7), merged.bookmarks)
        assertEquals(200, merged.lastPage)
        assertEquals("content://new/9", merged.uri)
        assertEquals(1, result.matched)
        assertEquals(0, result.waiting)
    }

    @Test
    fun restoreKeepsThePlaceOfABookAlreadyBeingRead() {
        val backup = BackupCodec.create(AppState(books = listOf(book("Walden", 320, "u", last = 200))), 1L)
        val reading = AppState(books = listOf(book("Walden", 320, "u", last = 12, opened = 50L)))
        assertEquals(12, BackupCodec.restore(reading, backup).state.books.single().lastPage)
    }

    @Test
    fun booksNotOnThisPhoneWaitAndAttachWhenImported() {
        val backup = BackupCodec.create(AppState(books = listOf(book("Walden", 320, "u", note("a"), last = 99))), 1L)
        val restored = BackupCodec.restore(AppState(), backup)
        assertEquals(0, restored.matched)
        assertEquals(1, restored.waiting)
        assertTrue(restored.state.books.isEmpty())

        val imported = book("Walden", 320, "content://new/1")
        val (attached, rest) = BackupCodec.attachPending(imported, restored.state.pending)
        assertEquals(listOf("a"), attached.annotations.map { it.id })
        assertEquals(99, attached.lastPage)
        assertTrue(rest.isEmpty())
    }

    @Test
    fun aDifferentEditionWithAnotherPageCountDoesNotGetTheNotes() {
        val backup = BackupCodec.create(AppState(books = listOf(book("Walden", 320, "u", note("a")))), 1L)
        val result = BackupCodec.restore(AppState(books = listOf(book("Walden", 300, "u2"))), backup)
        assertTrue(result.state.books.single().annotations.isEmpty())
        assertEquals(1, result.waiting)
    }

    @Test
    fun waitingNotesAreCarriedIntoTheNextBackup() {
        val first = BackupCodec.create(AppState(books = listOf(book("Walden", 320, "u", note("a")))), 1L)
        val phone = BackupCodec.restore(AppState(), first).state
        val second = BackupCodec.create(phone, 2L)
        assertEquals(listOf("walden|320"), second.books.map { it.key })
        assertEquals(listOf("a"), second.books[0].annotations.map { it.id })
    }

    @Test
    fun readingTimeKeepsTheLargerValueForEachDay() {
        val backup = BackupCodec.create(AppState(readingLog = mapOf("d1" to 600, "d2" to 100)), 1L)
        val result = BackupCodec.restore(AppState(readingLog = mapOf("d1" to 300, "d2" to 900, "d3" to 5)), backup)
        assertEquals(mapOf("d1" to 600, "d2" to 900, "d3" to 5), result.state.readingLog)
    }

    @Test
    fun restoreLeavesThePinAndLockAlone() {
        val backup = BackupCodec.create(AppState(settings = Settings(goalMinutes = 60, theme = ReaderTheme.SEPIA)), 1L)
        val phone = AppState(settings = Settings(pinHash = "h", pinSalt = "s", lockEnabled = true, goalMinutes = 30))
        val s = BackupCodec.restore(phone, backup).state.settings
        assertEquals("h", s.pinHash)
        assertTrue(s.lockEnabled)
        assertEquals(60, s.goalMinutes)
        assertEquals(ReaderTheme.SEPIA, s.theme)
    }
}
