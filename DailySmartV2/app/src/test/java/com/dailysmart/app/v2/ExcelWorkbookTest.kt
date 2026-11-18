package com.dailysmart.app.v2

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ExcelWorkbookTest {
    @Test
    fun userTextStartingWithEqualsIsExportedAsTextAndDashboardHasCharts() {
        val untrustedText = """=HYPERLINK("https://example.com","Open")"""
        val bytes = ExcelWorkbook.exportWorkspace(
            items = listOf(MoneyItem(untrustedText, 10.0, false, System.currentTimeMillis())),
            tasks = listOf(TaskItem(untrustedText)),
            habits = listOf(HabitItem(untrustedText)),
            scope = "all",
            language = "en"
        )

        val worksheetXml = mutableListOf<String>()
        val chartEntries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.startsWith("xl/worksheets/")) {
                    worksheetXml += zip.readBytes().toString(Charsets.UTF_8)
                } else if (entry.name.startsWith("xl/charts/")) {
                    chartEntries += entry.name
                    zip.readBytes()
                }
                zip.closeEntry()
            }
        }

        assertTrue(worksheetXml.isNotEmpty())
        assertTrue(worksheetXml.any { it.contains("=HYPERLINK") && it.contains("t=\"inlineStr\"") })
        assertTrue(worksheetXml.any { it.contains("<f>SUMIF") })
        assertTrue(worksheetXml.all { !it.contains("<f>HYPERLINK") })
        assertTrue(chartEntries.contains("xl/charts/chart1.xml"))
        assertTrue(chartEntries.contains("xl/charts/chart2.xml"))
    }
}
