package com.readingroom.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

sealed interface Screen {
    data object Library : Screen
    data class Reader(val bookId: String) : Screen
    data object Settings : Screen
    data object Apps : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Library)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun go(screen: Screen) {
        _screen.value = screen
    }

    fun showMessage(text: String?) {
        _message.value = text
    }

    private fun change(transform: (AppState) -> AppState) {
        _state.update(transform)
        store.save(_state.value)
    }

    // ----- library -----

    fun importBooks(uris: List<Uri>) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            var added = 0
            var failed = 0
            for (uri in uris) {
                if (_state.value.books.any { it.uri == uri.toString() }) continue
                val book = withContext(Dispatchers.IO) { readBook(uri) }
                if (book == null) failed++ else {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    change { it.copy(books = it.books + book) }
                    added++
                }
            }
            showMessage(
                when {
                    failed > 0 && added == 0 -> "That file could not be opened as a PDF."
                    failed > 0 -> "Added $added, but $failed could not be opened."
                    added > 0 -> "Added $added PDF${if (added == 1) "" else "s"}."
                    else -> "Already in your library."
                },
            )
        }
    }

    private fun readBook(uri: Uri): Book? {
        val context = getApplication<Application>()
        val doc = PdfDoc.open(context, uri) ?: return null
        val pages = doc.pageCount
        doc.close()
        if (pages == 0) return null
        var name: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0)
        }
        val title = (name ?: "Untitled").removeSuffix(".pdf").removeSuffix(".PDF").replace('_', ' ')
        return Book(
            id = UUID.randomUUID().toString(),
            title = title,
            uri = uri.toString(),
            pages = pages,
            addedAt = System.currentTimeMillis(),
        )
    }

    fun removeBook(id: String) {
        val book = _state.value.books.firstOrNull { it.id == id } ?: return
        runCatching {
            getApplication<Application>().contentResolver
                .releasePersistableUriPermission(Uri.parse(book.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        change { it.copy(books = it.books.filterNot { b -> b.id == id }) }
    }

    fun openBook(id: String) {
        change { s -> s.copy(books = s.books.map { if (it.id == id) it.copy(lastOpenedAt = System.currentTimeMillis()) else it }) }
        go(Screen.Reader(id))
    }

    fun setPage(id: String, page: Int) {
        change { s -> s.copy(books = s.books.map { if (it.id == id) it.copy(lastPage = page) else it }) }
    }

    fun toggleBookmark(id: String, page: Int) {
        change { s ->
            s.copy(
                books = s.books.map { b ->
                    if (b.id != id) b else b.copy(
                        bookmarks = if (page in b.bookmarks) b.bookmarks - page else (b.bookmarks + page).sorted(),
                    )
                },
            )
        }
    }

    // ----- notes and highlights -----

    private fun changeBook(id: String, transform: (Book) -> Book) =
        change { s -> s.copy(books = s.books.map { if (it.id == id) transform(it) else it }) }

    fun addAnnotation(bookId: String, annotation: Annotation) =
        changeBook(bookId) { it.copy(annotations = it.annotations + annotation) }

    fun updateAnnotation(bookId: String, annotation: Annotation) =
        changeBook(bookId) { b -> b.copy(annotations = b.annotations.map { if (it.id == annotation.id) annotation else it }) }

    fun deleteAnnotation(bookId: String, annotationId: String) =
        changeBook(bookId) { b -> b.copy(annotations = b.annotations.filterNot { it.id == annotationId }) }

    // ----- reading time -----

    fun addReadingSeconds(seconds: Int) {
        if (seconds <= 0) return
        val key = LocalDate.now().toString()
        change { it.copy(readingLog = it.readingLog + (key to ((it.readingLog[key] ?: 0) + seconds))) }
    }

    // ----- settings -----

    fun setTheme(theme: ReaderTheme) = change { it.copy(settings = it.settings.copy(theme = theme)) }

    fun setZoom(zoom: Float) = change { it.copy(settings = it.settings.copy(zoom = zoom.coerceIn(1f, 3f))) }

    fun setKeepAwake(on: Boolean) = change { it.copy(settings = it.settings.copy(keepAwake = on)) }

    fun setBrightness(value: Float) = change {
        val v = if (value < 0f) BRIGHTNESS_SYSTEM else value.coerceIn(0.05f, 1f)
        it.copy(settings = it.settings.copy(brightness = v))
    }

    fun setGoal(minutes: Int) = change { it.copy(settings = it.settings.copy(goalMinutes = minutes.coerceIn(5, 240))) }

    fun setPin(pin: String) {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        change { it.copy(settings = it.settings.copy(pinHash = hash, pinSalt = salt)) }
    }

    fun checkPin(pin: String): Boolean {
        val s = _state.value.settings
        val hash = s.pinHash ?: return false
        val salt = s.pinSalt ?: return false
        return PinHasher.verify(pin, salt, hash)
    }

    fun setLockEnabled(on: Boolean) = change { it.copy(settings = it.settings.copy(lockEnabled = on)) }
}
