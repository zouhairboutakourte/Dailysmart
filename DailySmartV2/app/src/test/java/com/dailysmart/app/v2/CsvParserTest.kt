package com.dailysmart.app.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {
    @Test
    fun parsesQuotedCommasAndEscapedQuotes() {
        val rows = parseCsv(
            "type,description,amount,currency\n" +
                "expense,\"Coffee, latte\",12,MAD\n" +
                "income,\"He said \"\"yes\"\"\",20,MAD"
        )

        assertEquals(3, rows.size)
        assertEquals("Coffee, latte", rows[1][1])
        assertEquals("He said \"yes\"", rows[2][1])
        assertEquals("20", rows[2][2])
    }

    @Test
    fun preservesNewlinesInsideQuotedDescriptions() {
        val rows = parseCsv(
            "type,description,amount\r\n" +
                "expense,\"first line\nsecond line\",10\r\n"
        )

        assertEquals(2, rows.size)
        assertEquals("first line\nsecond line", rows[1][1])
        assertEquals("10", rows[1][2])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnclosedQuotedField() {
        parseCsv("type,description\nexpense,\"unfinished")
    }
}
