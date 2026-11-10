package com.example.subdash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row of the sheet: NAME, DATE, TIMESLOT, SUBJECT, SECTION, SUBSTITUTE. */
data class Entry(
    val name: String,
    val date: LocalDate?,
    val timeslot: String,
    val minutes: Int?,      // start of the timeslot in minutes since midnight (for sorting)
    val subject: String,
    val section: String,
    val substitute: String
)

object SheetRepository {

    /** Accepts a full Google Sheets URL or a bare spreadsheet ID. */
    fun extractId(input: String): String? {
        val t = input.trim()
        if (t.isEmpty()) return null
        Regex("/d/([a-zA-Z0-9_-]+)").find(t)?.let { return it.groupValues[1] }
        return if (Regex("^[a-zA-Z0-9_-]{20,}$").matches(t)) t else null
    }

    suspend fun fetch(sheetInput: String, tab: String): List<Entry> = withContext(Dispatchers.IO) {
        val id = extractId(sheetInput)
            ?: throw IllegalArgumentException("Enter a valid Google Sheet link in Settings.")

        val url = buildString {
            append("https://docs.google.com/spreadsheets/d/$id/gviz/tq?tqx=out:csv&headers=1")
            if (tab.isNotBlank()) append("&sheet=").append(URLEncoder.encode(tab.trim(), "UTF-8"))
        }

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("Could not load sheet (HTTP ${conn.responseCode}). Is it shared as \"Anyone with the link – Viewer\"?")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.trimStart().startsWith("<")) {
                throw IllegalStateException("The sheet is not public. Share it as \"Anyone with the link – Viewer\".")
            }
            parseEntries(text)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Could not reach Google Sheets. Check your internet connection.")
        } finally {
            conn.disconnect()
        }
    }

    // ---------- parsing ----------

    private fun parseEntries(csv: String): List<Entry> {
        val rows = parseCsv(csv).filter { r -> r.any { it.isNotBlank() } }
        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map { it.trim().lowercase() }
        val hasHeader = header.any { it == "date" }
        val dataRows = if (hasHeader) rows.drop(1) else rows

        // Columns are matched by header name. Fallback order matches the form-responses sheet:
        // Timestamp | NAME | DATE | TIMESLOT | SUBJECT | SECTION | SUBSTITUTE
        fun idx(fallback: Int, match: (String) -> Boolean): Int =
            if (hasHeader) header.indexOfFirst(match).takeIf { it >= 0 } ?: fallback else fallback

        val iName = idx(1) { it == "name" }
        val iDate = idx(2) { it == "date" }
        val iTime = idx(3) { it == "timeslot" || it == "time slot" || it == "time" }
        val iSubject = idx(4) { it == "subject" }
        val iSection = idx(5) { it == "section" }
        val iSub = idx(6) { it.startsWith("substitut") }

        fun List<String>.col(i: Int) = getOrNull(i)?.trim().orEmpty()

        return dataRows.map { r ->
            val slot = r.col(iTime)
            Entry(
                name = r.col(iName),
                date = parseDate(r.col(iDate)),
                timeslot = slot,
                minutes = parseTime(slot),
                subject = r.col(iSubject),
                section = r.col(iSection),
                substitute = r.col(iSub)
            )
        }.filter { it.name.isNotBlank() || it.date != null }   // skip fully empty rows
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') { sb.append('"'); i++ }
                        else inQuotes = false
                    } else sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(sb.toString()); sb.setLength(0) }
                c == '\n' -> {
                    row.add(sb.toString()); sb.setLength(0)
                    rows.add(row); row = mutableListOf()
                }
                c == '\r' -> {}
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) { row.add(sb.toString()); rows.add(row) }
        return rows
    }

    private val dateFormats = listOf(
        "M/d/yyyy", "d/M/yyyy", "yyyy-M-d", "yyyy/M/d", "M/d/yy",
        "MMM d, yyyy", "MMMM d, yyyy", "d MMM yyyy", "d MMMM yyyy", "d-MMM-yyyy",
        "EEE, MMM d, yyyy", "EEEE, MMMM d, yyyy"
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    /** Tries US style (M/d/yyyy) first, then day-first, then written-out formats. */
    fun parseDate(s: String): LocalDate? {
        val t = s.trim()
        if (t.isEmpty()) return null
        for (f in dateFormats) {
            try { return LocalDate.parse(t, f) } catch (_: Exception) {}
        }
        return null
    }

    private val ampmRegex = Regex("(\\d{1,2})(?::(\\d{2}))?(?::\\d{2})?\\s*([AaPp])\\.?[Mm]")
    private val h24Regex = Regex("(\\d{1,2}):(\\d{2})")

    /** Reads the START of a timeslot: "11:20:00 AM", "8:00 AM - 9:00 AM", "13:30" all work. */
    fun parseTime(s: String): Int? {
        ampmRegex.find(s)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            val pm = m.groupValues[3].equals("p", ignoreCase = true)
            if (h in 1..12 && min in 0..59) return (h % 12 + if (pm) 12 else 0) * 60 + min
        }
        h24Regex.find(s)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) return h * 60 + min
        }
        return null
    }
}
