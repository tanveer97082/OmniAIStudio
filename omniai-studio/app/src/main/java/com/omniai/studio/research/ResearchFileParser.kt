package com.omniai.studio.research

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ResearchCsvPoint(
    val label: String,
    val value: Double
)

data class ResearchCsvSeries(
    val name: String,
    val points: List<ResearchCsvPoint>
)

data class ResearchCsvGraph(
    val fileName: String,
    val categoryLabel: String,
    val series: List<ResearchCsvSeries>
)

object ResearchFileParser {

    private const val MAX_NOTEBOOK_TEXT_CHARS = 18_000
    private const val MAX_CSV_CHARS = 500_000
    private const val MAX_CSV_ROWS = 1_000
    private const val MAX_CSV_COLUMNS = 20
    private val json = Json { ignoreUnknownKeys = true }

    fun extractNotebookText(rawJson: String): String {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val cells = root["cells"]?.jsonArray
            ?: throw IllegalArgumentException("This .ipynb file has no notebook cells.")
        val output = StringBuilder()

        cells.forEachIndexed { index, element ->
            if (output.length >= MAX_NOTEBOOK_TEXT_CHARS) return@forEachIndexed
            val cell = element as? JsonObject ?: return@forEachIndexed
            val type = cell["cell_type"]?.jsonPrimitive?.contentOrNull ?: "cell"
            val source = toText(cell["source"]).trim()
            if (source.isNotBlank()) {
                output.appendLine("=== ${type.uppercase()} CELL ${index + 1} (source only; never executed) ===")
                output.appendLine(source)
            }

            val outputs = cell["outputs"] as? JsonArray ?: return@forEachIndexed
            outputs.forEach { outputElement ->
                val outputObject = outputElement as? JsonObject ?: return@forEach
                val text = buildList {
                    outputObject["text"]?.let { add(toText(it)) }
                    val data = outputObject["data"] as? JsonObject
                    listOf("text/plain", "text/markdown").forEach { mimeType ->
                        data?.get(mimeType)?.let { add(toText(it)) }
                    }
                }.filter { it.isNotBlank() }.joinToString("\n").trim()
                if (text.isNotBlank() && output.length < MAX_NOTEBOOK_TEXT_CHARS) {
                    output.appendLine("Cell output:")
                    output.appendLine(text)
                }
            }
        }

        return output.toString().take(MAX_NOTEBOOK_TEXT_CHARS).trim()
    }

    fun parseCsvGraph(rawCsv: String, fileName: String): ResearchCsvGraph {
        require(rawCsv.length <= MAX_CSV_CHARS) {
            "CSV is larger than ${MAX_CSV_CHARS / 1_000} KB; choose a smaller file."
        }
        val rows = parseCsvRows(rawCsv).filter { row -> row.any { it.isNotBlank() } }
        require(rows.size >= 3) { "CSV needs a header and at least two data rows to graph." }

        val header = rows.first().take(MAX_CSV_COLUMNS)
        require(header.size >= 2) { "CSV must have a category column and at least one numeric column." }
        val body = rows.drop(1).take(MAX_CSV_ROWS)
        val categoryLabel = header.first().ifBlank { "Category" }
        val series = (1 until header.size).mapNotNull { column ->
            val points = body.mapIndexedNotNull { rowIndex, row ->
                val value = row.getOrNull(column)
                    ?.trim()
                    ?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() }
                    ?: return@mapIndexedNotNull null
                ResearchCsvPoint(
                    label = row.firstOrNull()?.trim()?.ifBlank { (rowIndex + 1).toString() }
                        ?: (rowIndex + 1).toString(),
                    value = value
                )
            }
            if (points.size < 2) null else {
                ResearchCsvSeries(
                    name = header[column].ifBlank { "Column ${column + 1}" },
                    points = points.take(MAX_CSV_ROWS)
                )
            }
        }.take(5)

        require(series.isNotEmpty()) { "No numeric CSV columns with at least two values were found." }
        return ResearchCsvGraph(fileName, categoryLabel, series)
    }

    private fun toText(element: JsonElement?): String = when (element) {
        null -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        is JsonArray -> element.joinToString("") { toText(it) }
        else -> ""
    }

    private fun parseCsvRows(rawCsv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < rawCsv.length && rows.size <= MAX_CSV_ROWS + 1) {
            val char = rawCsv[index]
            when {
                char == '"' && inQuotes && rawCsv.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    row += field.toString()
                    field.clear()
                }
                (char == '\n' || char == '\r') && !inQuotes -> {
                    row += field.toString()
                    field.clear()
                    rows += row.toList()
                    row.clear()
                    if (char == '\r' && rawCsv.getOrNull(index + 1) == '\n') index++
                }
                else -> field.append(char)
            }
            index++
        }

        require(!inQuotes) { "CSV contains an unterminated quoted field." }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row.toList()
        }
        return rows
    }
}
