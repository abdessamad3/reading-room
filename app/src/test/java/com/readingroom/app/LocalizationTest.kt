package com.readingroom.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the language files against each other. A missing entry would fall back to English, and a
 * wrong placeholder (like a %d where the English text has %s) crashes the screen in that language.
 */
class LocalizationTest {
    private class Entry(val items: Map<String, String>, val isPlural: Boolean)

    private fun load(folder: String): Map<String, Entry> {
        // Unit tests run with the app module as the working directory.
        val file = File("src/main/res/$folder/strings.xml")
        assertTrue("Missing ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = LinkedHashMap<String, Entry>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i)
            out[e.attributes.getNamedItem("name").nodeValue] = Entry(mapOf("other" to e.textContent), false)
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val p = plurals.item(i)
            val items = LinkedHashMap<String, String>()
            for (j in 0 until p.childNodes.length) {
                val c = p.childNodes.item(j)
                if (c.nodeName == "item") items[c.attributes.getNamedItem("quantity").nodeValue] = c.textContent
            }
            out[p.attributes.getNamedItem("name").nodeValue] = Entry(items, true)
        }
        return out
    }

    private fun placeholders(text: String): List<String> =
        Regex("%(\\d+\\$)?[sd]").findAll(text.replace("%%", "")).map { it.value }.sorted().toList()

    private val en = load("values")
    private val others = mapOf("ar" to load("values-ar"), "fr" to load("values-fr"))

    @Test
    fun everyLanguageHasEveryEntry() {
        for ((lang, strings) in others) {
            assertEquals("Entries missing or extra in $lang", en.keys, strings.keys)
        }
    }

    @Test
    fun stringsUseTheSamePlaceholdersAsEnglish() {
        for ((lang, strings) in others) {
            for ((name, entry) in en) {
                if (entry.isPlural) continue
                assertEquals(
                    "Placeholders differ in $lang for '$name'",
                    placeholders(entry.items.getValue("other")),
                    placeholders(strings.getValue(name).items.getValue("other")),
                )
            }
        }
    }

    @Test
    fun pluralFormsOnlyUseTheNumberTheyAreGiven() {
        for ((lang, strings) in others) {
            for ((name, entry) in en) {
                if (!entry.isPlural) continue
                val translated = strings.getValue(name)
                assertTrue("'$name' in $lang must be a plural", translated.isPlural)
                assertTrue("'$name' in $lang needs an 'other' form", "other" in translated.items)
                for ((quantity, text) in translated.items) {
                    val used = placeholders(text)
                    assertTrue("'$name'/$quantity in $lang uses $used, expected only %d", used.all { it == "%d" })
                }
            }
        }
    }

    @Test
    fun arabicPluralsCoverEverySixForms() {
        val needed = setOf("zero", "one", "two", "few", "many", "other")
        for ((name, entry) in load("values-ar")) {
            if (entry.isPlural) assertEquals("Arabic plural '$name' should list all six forms", needed, entry.items.keys)
        }
    }

    @Test
    fun noLanguageLeavesAnEntryEmpty() {
        for ((lang, strings) in others + ("en" to en)) {
            for ((name, entry) in strings) {
                for ((quantity, text) in entry.items) {
                    assertTrue("'$name'/$quantity in $lang is empty", text.isNotBlank() || name == "export_separator")
                }
            }
        }
    }
}
