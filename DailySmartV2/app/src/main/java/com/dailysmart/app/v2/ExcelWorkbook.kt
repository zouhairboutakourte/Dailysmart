package com.dailysmart.app.v2

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Minimal OOXML .xlsx reader/writer for DailySmart finance records.
 * Uses Android's built-in ZIP and XML APIs; no third-party library or network required.
 */
object ExcelWorkbook {
    // Defensive limits for user-selected workbooks. XLSX is a ZIP container, so
    // bound both the uploaded file and the expanded XML entries to resist ZIP bombs.
    private const val MAX_INPUT_BYTES = 10 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 5 * 1024 * 1024
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 10 * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 64

    fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_INPUT_BYTES) { "File is too large. Maximum supported size is 10 MB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun exportFinance(items: List<MoneyItem>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""")
            put(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
            put(zip, "xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Finance" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put(zip, "xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""")
            val rows = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                val headers = listOf("Type", "Description", "Amount", "Currency")
                appendRow(1, headers.mapIndexed { i, v -> cell(i, 1, v) }.joinToString(""))
                items.forEachIndexed { index, item ->
                    val row = index + 2
                    val type = if (item.income) "Income" else "Expense"
                    appendRow(row, cell(0, row, type) + cell(1, row, item.title) +
                        """<c r="C$row" t="n"><v>${item.amount}</v></c>""" + cell(3, row, "MAD"))
                }
                append("</sheetData></worksheet>")
            }
            put(zip, "xl/worksheets/sheet1.xml", rows)
        }
        return out.toByteArray()
    }

    fun importFinance(bytes: ByteArray): List<MoneyItem> {
        require(bytes.isNotEmpty()) { "The selected workbook is empty." }
        require(bytes.size <= MAX_INPUT_BYTES) { "Workbook is too large. Maximum supported size is 10 MB." }

        val entries = mutableMapOf<String, ByteArray>()
        var entryCount = 0
        var totalExpanded = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ZIP_ENTRIES) { "Workbook contains too many internal files." }
                if (!entry.isDirectory) {
                    val content = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var entryBytes = 0
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes += count
                        totalExpanded += count
                        require(entryBytes <= MAX_ENTRY_BYTES) { "Workbook contains an oversized internal file." }
                        require(totalExpanded <= MAX_TOTAL_UNCOMPRESSED_BYTES) { "Workbook expands beyond the 10 MB safety limit." }
                        content.write(buffer, 0, count)
                    }
                    entries[entry.name] = content.toByteArray()
                }
                zip.closeEntry()
            }
        }
        val sheet = entries["xl/worksheets/sheet1.xml"]
            ?: throw IllegalArgumentException("The workbook has no first worksheet.")
        val shared = entries["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
        val rows = readRows(sheet, shared)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        val typeCol = header.indexOfFirst { it in listOf("type", "kind", "transaction", "نوع", "النوع", "type d'opération") }.let { if (it < 0) 0 else it }
        val descriptionCol = header.indexOfFirst { it in listOf("description", "title", "label", "details", "الوصف", "البيان") }.let { if (it < 0) 1 else it }
        val amountCol = header.indexOfFirst { it in listOf("amount", "value", "montant", "المبلغ", "المجموع") }.let { if (it < 0) 2 else it }
        return rows.drop(1).mapNotNull { row ->
            val amount = row.getOrNull(amountCol)?.replace(",", ".")?.replace(Regex("[^0-9.\\-]"), "")?.toDoubleOrNull()
                ?: return@mapNotNull null
            if (!amount.isFinite() || amount <= 0.0) return@mapNotNull null
            val type = row.getOrNull(typeCol).orEmpty().lowercase()
            val income = type.contains("income") || type.contains("revenu") || type.contains("دخل") || type.contains("recette")
            val description = row.getOrNull(descriptionCol).orEmpty().ifBlank { if (income) "Income" else "Expense" }
            if (description.length > 500) return@mapNotNull null
            MoneyItem(description, amount, income)
        }
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        val result = mutableListOf<String>()
        var inSi = false
        var current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") { inSi = true; current = StringBuilder() }
                XmlPullParser.TEXT -> if (inSi) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") { result += current.toString(); inSi = false }
            }
            event = parser.next()
        }
        return result
    }

    private fun readRows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        val result = mutableListOf<List<String>>()
        var row: MutableMap<Int, String>? = null
        var column = 0
        var cellType = ""
        var value = StringBuilder()
        var inValue = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = sortedMapOf<Int, String>()
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r").orEmpty()
                        column = ref.takeWhile { it.isLetter() }.fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        value = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        val text = if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                        row?.set(column, text)
                    }
                    "row" -> {
                        val cells = row.orEmpty()
                        val max = cells.keys.maxOrNull() ?: -1
                        if (max >= 0) result += (0..max).map { cells[it].orEmpty() }
                        row = null
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun cell(column: Int, row: Int, value: String): String {
        val ref = columnName(column) + row
        val escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")
        return """<c r="$ref" t="inlineStr"><is><t xml:space="preserve">$escaped</t></is></c>"""
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        var result = ""
        while (n > 0) { val rem = (n - 1) % 26; result = ('A' + rem) + result; n = (n - 1) / 26 }
        return result
    }

    private fun StringBuilder.appendRow(index: Int, content: String) {
        append("""<row r="$index">$content</row>""")
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
