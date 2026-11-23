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

    private fun exportableMoney(items: List<MoneyItem>): List<MoneyItem> =
        items.filter { it.amount.isFinite() && it.amount >= 0.005 }

    fun exportFinance(items: List<MoneyItem>): ByteArray {
        val exportItems = exportableMoney(items)
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
                val headers = listOf("Type", "Description", "Amount", "Currency", "Date")
                appendRow(1, headers.mapIndexed { i, v -> cell(i, 1, v) }.joinToString(""))
                exportItems.forEachIndexed { index, item ->
                    val row = index + 2
                    val type = if (item.income) "Income" else "Expense"
                    appendRow(row, cell(0, row, type) + cell(1, row, item.title) +
                        """<c r="C$row" t="n"><v>${item.amount}</v></c>""" + cell(3, row, "MAD") + cell(4, row, FinanceAnalytics.dateString(item.createdAt)))
                }
                append("</sheetData></worksheet>")
            }
            put(zip, "xl/worksheets/sheet1.xml", rows)
        }
        return out.toByteArray()
    }

    /** Multi-sheet workbook assembled locally from app records. */
    fun exportWorkspace(items: List<MoneyItem>, tasks: List<TaskItem>, habits: List<HabitItem>, scope: String, language: String): ByteArray {
        val exportItems = exportableMoney(items)
        val sheets = mutableListOf<Pair<String,String>>()
        fun makeSheet(name:String, rows:List<List<String>>):Pair<String,String> {
            val xml=buildString { append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"); rows.forEachIndexed { ri,row -> append("<row r=\"${ri+1}\">"); row.forEachIndexed { ci,v -> append(cell(ci,ri+1,v)) }; append("</row>") }; append("</sheetData></worksheet>") }; return name to xml
        }
        if(scope=="all"||scope=="finance"){
            val income=exportItems.filter{it.income}.sumOf{it.amount}; val expense=exportItems.filterNot{it.income}.sumOf{it.amount}
            val cats=items.filterNot{it.income}.groupBy{FinanceAnalytics.categoryFor(it.title)}.mapValues{it.value.sumOf{m->m.amount}}.toList().sortedByDescending{it.second}
            val dash=mutableListOf(listOf("DailySmart Dashboard"),listOf("Generated",java.time.LocalDate.now().toString()),listOf("Income MAD",income.toString()),listOf("Expenses MAD",expense.toString()),listOf("Net balance MAD",(income-expense).toString()),listOf("Transactions",exportItems.size.toString()),listOf("Top category",cats.firstOrNull()?.first?:"—"),listOf("Category","Expenses MAD","Visual bar"))
            val maxCategory=cats.maxOfOrNull{it.second}?:0.0; cats.forEach{p->dash+=listOf(p.first,p.second.toString(),"█".repeat(if(maxCategory>0)(p.second/maxCategory*20).toInt().coerceAtLeast(1) else 0))}
            sheets+=makeSheet("Dashboard",dash)
            sheets+=makeSheet("Finance",listOf(listOf("Type","Description","Amount MAD","Date","Category"))+exportItems.map{listOf(if(it.income)"Income" else "Expense",it.title,it.amount.toString(),FinanceAnalytics.dateString(it.createdAt),if(it.income)"Income" else FinanceAnalytics.categoryFor(it.title))})
            val monthly=exportItems.filter{it.createdAt>0L}.groupBy{java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1)}.toList().sortedBy{it.first}
            val monthlyTotals=monthly.map{m->Triple(m.first,m.second.filter{it.income}.sumOf{it.amount},m.second.filterNot{it.income}.sumOf{it.amount})}
            val maxMonthly=monthlyTotals.maxOfOrNull{it.third}?:0.0
            sheets+=makeSheet("Monthly",listOf(listOf("Month","Income MAD","Expenses MAD","Net MAD","Expense visual"))+monthlyTotals.map{m->listOf(m.first.toString(),m.second.toString(),m.third.toString(),(m.second-m.third).toString(),"█".repeat(if(maxMonthly>0)(m.third/maxMonthly*20).toInt().coerceAtLeast(1) else 0))})
            sheets+=makeSheet("Categories",listOf(listOf("Category","Expenses MAD","Visual bar"))+cats.map{p->listOf(p.first,p.second.toString(),"█".repeat(if(maxCategory>0)(p.second/maxCategory*20).toInt().coerceAtLeast(1) else 0))})
        }
        if(scope=="all"||scope=="tasks") sheets+=makeSheet("Tasks",listOf(listOf("Task","Status"))+tasks.map{listOf(it.title,if(it.done)"Completed" else "Pending")})
        if(scope=="all"||scope=="habits") sheets+=makeSheet("Habits",listOf(listOf("Habit","Completed today"))+habits.map{listOf(it.title,if(it.completedToday)"Yes" else "No")})
        val out=ByteArrayOutputStream(); ZipOutputStream(out).use{zip->
            val overrides=sheets.indices.joinToString(""){i->"<Override PartName=\"/xl/worksheets/sheet${i+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"}
            put(zip,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>$overrides</Types>")
            put(zip,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            val nodes=sheets.mapIndexed{i,s->"<sheet name=\"${s.first}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>"}.joinToString("")
            put(zip,"xl/workbook.xml","<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>$nodes</sheets></workbook>")
            val rels=sheets.mapIndexed{i,_->"<Relationship Id=\"rId${i+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i+1}.xml\"/>"}.joinToString("")
            put(zip,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$rels</Relationships>")
            sheets.forEachIndexed{i,s->put(zip,"xl/worksheets/sheet${i+1}.xml",s.second)}
        }; return out.toByteArray()
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
        val dateCol = header.indexOfFirst { it in listOf("date", "transaction date", "التاريخ") }
        return rows.drop(1).mapNotNull { row ->
            val amount = row.getOrNull(amountCol)?.replace(",", ".")?.replace(Regex("[^0-9.\\-]"), "")?.toDoubleOrNull()
                ?: return@mapNotNull null
            if (!amount.isFinite() || amount <= 0.0) return@mapNotNull null
            val type = row.getOrNull(typeCol).orEmpty().lowercase()
            val income = type.contains("income") || type.contains("revenu") || type.contains("دخل") || type.contains("recette")
            val description = row.getOrNull(descriptionCol).orEmpty().ifBlank { if (income) "Income" else "Expense" }
            if (description.length > 500) return@mapNotNull null
            MoneyItem(description, amount, income, FinanceAnalytics.timestampFromDate(row.getOrNull(dateCol).orEmpty()))
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
