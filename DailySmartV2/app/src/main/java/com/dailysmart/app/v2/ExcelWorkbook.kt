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
                val headers = listOf("Type", "Description", "Amount", "Currency", "Date")
                appendRow(1, headers.mapIndexed { i, v -> cell(i, 1, v) }.joinToString(""))
                items.forEachIndexed { index, item ->
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
        val sheets = mutableListOf<Pair<String,String>>()
        fun makeSheet(name:String, rows:List<List<String>>):Pair<String,String> {
            val xml=buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
                rows.forEachIndexed { ri,row ->
                    append("<row r=\""+(ri+1)+"\">")
                    row.forEachIndexed { ci,v ->
                        append(when {
                            v.startsWith("\u0001=") -> formulaCell(ci, ri+1, v.drop(2))
                            v.toDoubleOrNull() != null -> numberCell(ci, ri+1, v)
                            else -> cell(ci, ri+1, v)
                        })
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            return name to xml
        }
        if(scope=="all"||scope=="finance"){
            val income=items.filter{it.income}.sumOf{it.amount}; val expense=items.filterNot{it.income}.sumOf{it.amount}
            val cats=items.filterNot{it.income}.groupBy{FinanceAnalytics.categoryFor(it.title)}.mapValues{it.value.sumOf{m->m.amount}}.toList().sortedByDescending{it.second}
            val lastFinanceRow=(items.size+1).coerceAtLeast(2)
            val dash=mutableListOf(listOf("DailySmart Dashboard"),listOf("Generated",java.time.LocalDate.now().toString()),listOf("Income MAD","\u0001=SUMIF(Finance!\$A\$2:\$A\$"+lastFinanceRow+",\"Income\",Finance!\$C\$2:\$C\$"+lastFinanceRow+")"),listOf("Expenses MAD","=SUMIF(Finance!\$A\$2:\$A\$"+lastFinanceRow+",\"Expense\",Finance!\$C\$2:\$C\$"+lastFinanceRow+")"),listOf("Net balance MAD","\u0001=B3-B4"),listOf("Transactions",items.size.toString()),listOf("Top category",cats.firstOrNull()?.first?:"—"),listOf("Category","Expenses MAD","Visual bar"))
            val maxCategory=cats.maxOfOrNull{it.second}?:0.0; cats.forEach{p->dash+=listOf(p.first,p.second.toString(),"█".repeat(if(maxCategory>0)(p.second/maxCategory*20).toInt().coerceAtLeast(1) else 0))}
            sheets+=makeSheet("Dashboard",dash)
            sheets+=makeSheet("Finance",listOf(listOf("Type","Description","Amount MAD","Date","Category"))+items.map{listOf(if(it.income)"Income" else "Expense",it.title,it.amount.toString(),FinanceAnalytics.dateString(it.createdAt),if(it.income)"Income" else FinanceAnalytics.categoryFor(it.title))})
            val monthly=items.filter{it.createdAt>0L}.groupBy{java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1)}.toList().sortedBy{it.first}
            val monthlyTotals=monthly.map{m->Triple(m.first,m.second.filter{it.income}.sumOf{it.amount},m.second.filterNot{it.income}.sumOf{it.amount})}
            val maxMonthly=monthlyTotals.maxOfOrNull{it.third}?:0.0
            sheets+=makeSheet("Monthly",listOf(listOf("Month","Income MAD","Expenses MAD","Net MAD","Expense visual"))+monthlyTotals.map{m->listOf(m.first.toString(),m.second.toString(),m.third.toString(),(m.second-m.third).toString(),"█".repeat(if(maxMonthly>0)(m.third/maxMonthly*20).toInt().coerceAtLeast(1) else 0))})
            sheets+=makeSheet("Categories",listOf(listOf("Category","Expenses MAD","Visual bar"))+cats.map{p->listOf(p.first,p.second.toString(),"█".repeat(if(maxCategory>0)(p.second/maxCategory*20).toInt().coerceAtLeast(1) else 0))})
        }
        if(scope=="all"||scope=="tasks") sheets+=makeSheet("Tasks",listOf(listOf("Task","Status"))+tasks.map{listOf(it.title,if(it.done)"Completed" else "Pending")})
        if(scope=="all"||scope=="habits") sheets+=makeSheet("Habits",listOf(listOf("Habit","Completed today"))+habits.map{listOf(it.title,if(it.completedToday)"Yes" else "No")})
        val chartXmls = mutableListOf<String>()
        val financeIncluded = scope == "all" || scope == "finance"
        val chartCategories = if (financeIncluded) items.filterNot { it.income }.groupBy { FinanceAnalytics.categoryFor(it.title) }
            .mapValues { it.value.sumOf { m -> m.amount } }.toList().sortedByDescending { it.second } else emptyList()
        val chartMonthly = if (financeIncluded) items.filter { it.createdAt > 0L }
            .groupBy { java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1) }
            .toList().sortedBy { it.first }.map { it.first.toString() to it.second.filterNot { m -> m.income }.sumOf { m -> m.amount } } else emptyList()
        if (chartCategories.isNotEmpty()) chartXmls += pieChartXml("Categories", chartCategories.map { it.first }, chartCategories.map { it.second })
        if (chartMonthly.isNotEmpty()) chartXmls += barChartXml("Monthly", chartMonthly.map { it.first }, chartMonthly.map { it.second })
        if (chartXmls.isNotEmpty()) {
            val dashboardIndex = sheets.indexOfFirst { it.first == "Dashboard" }
            if (dashboardIndex >= 0) {
                val dashboard = sheets[dashboardIndex]
                val withDrawing = dashboard.second
                    .replace("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">", "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                    .replace("</worksheet>", "<drawing r:id=\"rId1\"/></worksheet>")
                sheets[dashboardIndex] = dashboard.first to withDrawing
            }
        }
        val out=ByteArrayOutputStream(); ZipOutputStream(out).use{zip->
            val overrides=sheets.indices.joinToString(""){i->"<Override PartName=\"/xl/worksheets/sheet"+(i+1)+".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"}
            val chartOverrides = if (chartXmls.isEmpty()) "" else "<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>" + chartXmls.indices.joinToString("") { i -> "<Override PartName=\"/xl/charts/chart"+(i+1)+".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/>" }
            put(zip,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"+overrides+chartOverrides+"</Types>")
            put(zip,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            val nodes=sheets.mapIndexed{i,s->"<sheet name=\""+s.first+"\" sheetId=\""+(i+1)+"\" r:id=\"rId"+(i+1)+"\"/>"}.joinToString("")
            put(zip,"xl/workbook.xml","<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"+nodes+"</sheets><calcPr calcId=\"191029\" fullCalcOnLoad=\"1\" forceFullCalc=\"1\"/></workbook>")
            val rels=sheets.mapIndexed{i,_->"<Relationship Id=\"rId"+(i+1)+"\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet"+(i+1)+".xml\"/>"}.joinToString("")
            put(zip,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+rels+"</Relationships>")
            sheets.forEachIndexed{i,s->put(zip,"xl/worksheets/sheet"+(i+1)+".xml",s.second)}
            if (chartXmls.isNotEmpty()) {
                put(zip,"xl/worksheets/_rels/sheet1.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing1.xml\"/></Relationships>")
                put(zip,"xl/drawings/drawing1.xml",drawingXml(chartXmls.size))
                val drawingRels=chartXmls.indices.joinToString("") { i -> "<Relationship Id=\"rId"+(i+1)+"\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart\" Target=\"../charts/chart"+(i+1)+".xml\"/>" }
                put(zip,"xl/drawings/_rels/drawing1.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+drawingRels+"</Relationships>")
                chartXmls.forEachIndexed { i, xml -> put(zip,"xl/charts/chart"+(i+1)+".xml",xml) }
            }
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
        val typeHeader = header.indexOfFirst { it in listOf("type", "kind", "transaction", "نوع", "النوع", "type d'opération") }
        val descriptionHeader = header.indexOfFirst { it in listOf("description", "title", "label", "details", "الوصف", "البيان") }
        val amountHeader = header.indexOfFirst { it in listOf("amount", "value", "montant", "المبلغ", "المجموع", "amount mad") }
        val hasHeader = typeHeader >= 0 || descriptionHeader >= 0 || amountHeader >= 0
        val typeCol = typeHeader.takeIf { it >= 0 } ?: 0
        val descriptionCol = descriptionHeader.takeIf { it >= 0 } ?: 1
        val amountCol = amountHeader.takeIf { it >= 0 } ?: 2
        val dateCol = header.indexOfFirst { it in listOf("date", "transaction date", "التاريخ") }
        val dataRows = if (hasHeader) rows.drop(1) else rows
        return dataRows.mapNotNull { row ->
            val amount = MoneyAmountParser.parse(row.getOrNull(amountCol).orEmpty())
                ?: return@mapNotNull null
            val type = row.getOrNull(typeCol).orEmpty().lowercase()
            val income = type.contains("income") || type.contains("revenu") || type.contains("دخل") || type.contains("recette") || type.contains("salary") || type.contains("salaire")
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


    private fun formulaCell(column: Int, row: Int, formula: String): String {
        val ref = columnName(column) + row
        return "<c r=\""+ref+"\"><f>"+formula+"</f><v></v></c>"
    }

    private fun numberCell(column: Int, row: Int, value: String): String {
        val ref = columnName(column) + row
        return "<c r=\""+ref+"\" t=\"n\"><v>"+value+"</v></c>"
    }

    private fun drawingXml(count: Int): String {
        val anchors = (1..count).joinToString("") { index ->
            val fromCol = if (index == 1) 6 else 13
            val toCol = if (index == 1) 12 else 19
            val fromRow = if (index == 1) 1 else 16
            val toRow = if (index == 1) 15 else 30
            "<xdr:twoCellAnchor><xdr:from><xdr:col>"+fromCol+"</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>"+fromRow+"</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:to><xdr:col>"+toCol+"</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>"+toRow+"</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to><xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr><xdr:cNvPr id=\""+(index+1)+"\" name=\"Chart "+index+"\"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr><xdr:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/></xdr:xfrm><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"><c:chart xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" r:id=\"rId"+index+"\"/></a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"+anchors+"</xdr:wsDr>"
    }

    private fun pieChartXml(sheet: String, labels: List<String>, values: List<Double>): String {
        val end = labels.size + 1
        val labelFormula = "'"+sheet+"'!\$A\$2:\$A\$"+end
        val valueFormula = "'"+sheet+"'!\$B\$2:\$B\$"+end
        val labelCache = labels.mapIndexed { i, v -> "<c:pt idx=\""+i+"\"><c:v>"+xmlEscape(v)+"</c:v></c:pt>" }.joinToString("")
        val valueCache = values.mapIndexed { i, v -> "<c:pt idx=\""+i+"\"><c:v>"+v+"</c:v></c:pt>" }.joinToString("")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><c:chart><c:plotArea><c:layout/><c:pieChart><c:varyColors val=\"1\"/><c:ser><c:idx val=\"0\"/><c:order val=\"0\"/><c:cat><c:strRef><c:f>"+labelFormula+"</c:f><c:strCache><c:ptCount val=\""+labels.size+"\"/>"+labelCache+"</c:strCache></c:strRef></c:cat><c:val><c:numRef><c:f>"+valueFormula+"</c:f><c:numCache><c:formatCode>General</c:formatCode><c:ptCount val=\""+values.size+"\"/>"+valueCache+"</c:numCache></c:numRef></c:val></c:ser><c:dLbls><c:showLegendKey val=\"0\"/><c:showPercent val=\"1\"/><c:showCategoryName val=\"1\"/></c:dLbls><c:firstSliceAng val=\"0\"/></c:pieChart><c:legend><c:legendPos val=\"r\"/><c:layout/></c:legend><c:plotVisOnly val=\"1\"/></c:plotArea><c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart></c:chartSpace>"
    }

    private fun barChartXml(sheet: String, labels: List<String>, values: List<Double>): String {
        val end = labels.size + 1
        val labelFormula = "'"+sheet+"'!\$A\$2:\$A\$"+end
        val valueFormula = "'"+sheet+"'!\$C\$2:\$C\$"+end
        val labelCache = labels.mapIndexed { i, v -> "<c:pt idx=\""+i+"\"><c:v>"+xmlEscape(v)+"</c:v></c:pt>" }.joinToString("")
        val valueCache = values.mapIndexed { i, v -> "<c:pt idx=\""+i+"\"><c:v>"+v+"</c:v></c:pt>" }.joinToString("")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><c:chart><c:plotArea><c:layout/><c:barChart><c:barDir val=\"col\"/><c:grouping val=\"clustered\"/><c:varyColors val=\"0\"/><c:ser><c:idx val=\"0\"/><c:order val=\"0\"/><c:cat><c:strRef><c:f>"+labelFormula+"</c:f><c:strCache><c:ptCount val=\""+labels.size+"\"/>"+labelCache+"</c:strCache></c:strRef></c:cat><c:val><c:numRef><c:f>"+valueFormula+"</c:f><c:numCache><c:formatCode>General</c:formatCode><c:ptCount val=\""+values.size+"\"/>"+valueCache+"</c:numCache></c:numRef></c:val></c:ser><c:gapWidth val=\"80\"/><c:axId val=\"123456\"/><c:axId val=\"123457\"/></c:barChart><c:catAx><c:axId val=\"123456\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:axPos val=\"b\"/><c:tickLblPos val=\"nextTo\"/><c:crossAx val=\"123457\"/><c:crosses val=\"autoZero\"/></c:catAx><c:valAx><c:axId val=\"123457\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:axPos val=\"l\"/><c:majorGridlines/><c:numFmt formatCode=\"#,##0.00\" sourceLinked=\"0\"/><c:crossAx val=\"123456\"/><c:crosses val=\"autoZero\"/><c:crossesAt val=\"0\"/></c:valAx><c:legend><c:legendPos val=\"b\"/><c:layout/></c:legend><c:plotVisOnly val=\"1\"/></c:plotArea><c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart></c:chartSpace>"
    }

    private fun xmlEscape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

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
