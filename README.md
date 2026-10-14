# Reading Room

An Android app that turns a phone into a quiet place to read PDFs. It becomes the Home screen, hides every other app behind a PIN-protected focus lock, and gives you a reader with page colors, zoom, bookmarks and a daily reading goal.

## Reading tools

- **Highlighter:** pick a color in the bottom bar and drag over the text. Tap a highlight to add a note, change its color or delete it.
- **Notes:** pick the note tool and tap where the note goes.
- **Notes list:** the notes icon in the top bar lists every highlight (with a cut-out of the page) and note. Tap one to jump to its page, or copy everything as text.
- **Also:** bookmarks, Paper/Sepia/Night, zoom, brightness, page slider, reading goal and streak.

Highlights are drawn over the page picture, so they mark an area rather than selected words. Search, table of contents and dictionary lookup need text extraction, which Android's built-in PDF renderer does not offer.

## How the lock works

| Mode | Setup | What it does |
|---|---|---|
| Screen pinning | None. Turn the lock on in Settings. | Android pins the app. Holding Back + Recents undoes it. |
| Locked device | One-time `adb` command (below) | Home, Recents and notifications are blocked and the lock returns after a restart. |

Locked device setup, from a computer with `adb`, with the phone connected over USB and no Google accounts signed in (add them back afterwards):

```
adb shell dpm set-device-owner com.readingroom.app/.AdminReceiver
```

To remove it later, turn the lock off with your PIN, then uninstall the app.

## Build

GitHub Actions runs the unit tests and builds a debug APK on every push. Download it from the **Debug APK (latest)** release, or from the workflow run's artifacts.

Locally: `./gradlew testDebugUnitTest assembleDebug` (needs JDK 17 and the Android SDK).

## Built with

Kotlin, Jetpack Compose, Android `PdfRenderer`, `SharedPreferences` for storage, Lock Task mode and Device Admin for the lock.
