package com.dailysmart.app.v2

internal fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> { field.append('"'); index++ }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> { row.add(field.toString()); field.setLength(0) }
            (char == '\n' || char == '\r') && !quoted -> {
                row.add(field.toString()); field.setLength(0)
                if (row.any { it.isNotBlank() }) rows.add(row)
                row = mutableListOf()
                if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
            }
            else -> field.append(char)
        }
        index++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); if (row.any { it.isNotBlank() }) rows.add(row) }
    require(!quoted) { "CSV contains an unterminated quoted field." }
    return rows
}
