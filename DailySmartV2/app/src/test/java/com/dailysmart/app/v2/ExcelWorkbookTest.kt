package com.dailysmart.app.v2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ExcelWorkbookTest {
    @Test
    fun userTextStartingWithEqualsIsExportedAsTextNotFormula() {
        val untrustedText = """=HYPERLINK("https://example.com","Open")"""
        val bytes = ExcelWorkbook.exportWorkspace(
            items = listOf(MoneyItem(untrustedText, 10.0, false, System.currentTimeMillis())),
            tasks = listOf(TaskItem(untrustedText)),
            habits = listOf(HabitItem(untrustedText)),
            scope = "all",
            language = "en"
        )

        val worksheetXml = mutableListOf<String>()\n        val chartEntries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.startsWith("xl/worksheets/")) {
                    worksheetXml += zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }

        assertTrue(worksheetXml.isNotEmpty())
        assertTrue(worksheetXml.any { it.contains("=HYPERLINK") })
        assertTrue(worksheetXml.all { !it.contains("<f>") })
        assertTrue(worksheetXml.any { it.contains("t=\"inlineStr\"") })
    }
}
