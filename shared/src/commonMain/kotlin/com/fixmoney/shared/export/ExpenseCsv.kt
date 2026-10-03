package com.fixmoney.shared.export

import com.fixmoney.shared.db.ExpenseDao
import com.fixmoney.shared.db.ExpenseEntity
import com.fixmoney.shared.platform.isoToEpochDay

/** What the Export Data dialog collected - one whole month, or an explicit range. */
sealed class ExportRange {
    data class Monthly(val month: String, val label: String) : ExportRange()
    data class Custom(val fromIso: String, val toIso: String) : ExportRange()
}

sealed class ExportResult {
    data class Success(val message: String, val location: ExportLocation) : ExportResult()
    data class Failure(val message: String) : ExportResult()
}

/**
 * Where a written export ended up.
 *
 * [display] is for the user ("Downloads/FixMoney Exports/…"); [handle] is the
 * platform's own reference to the file - a `content://` URI on Android, a path
 * on iOS - and is what [ExportFileWriter.open] needs to hand it to another app.
 */
data class ExportLocation(val display: String, val handle: String)

/**
 * Port of the old `ExpenseCsvExporter`. The **CSV itself is shared** - it is
 * string building and row filtering, identical on both platforms - and only
 * handing the finished file to the OS is `expect`/`actual`.
 *
 * CSV, not a real `.xlsx`, for the old app's reason: a spreadsheet app opens it
 * natively and it avoids pulling in something like Apache POI for nothing.
 */
class ExpenseExporter(
    private val dao: ExpenseDao,
    private val writer: ExportFileWriter
) {

    suspend fun export(range: ExportRange): ExportResult {
        val all = dao.getExpensesOnce()
        val rows = when (range) {
            is ExportRange.Monthly -> all.filter { it.date.startsWith(range.month) }
            is ExportRange.Custom -> {
                val from = isoToEpochDay(range.fromIso)
                val to = isoToEpochDay(range.toIso)
                if (from == null || to == null) emptyList()
                else all.filter { row ->
                    val d = isoToEpochDay(row.date) ?: return@filter false
                    d in from..to
                }
            }
        }.sortedBy { it.date }

        if (rows.isEmpty()) return ExportResult.Failure("No expenses found for that period")

        return try {
            val location = writer.write(fileName(range), buildCsv(rows))
            ExportResult.Success(
                "Saved ${rows.size} expenses to ${location.display}",
                location
            )
        } catch (e: Exception) {
            ExportResult.Failure(e.message ?: "Export failed")
        }
    }

    private fun fileName(range: ExportRange): String = when (range) {
        is ExportRange.Monthly -> "FixMoney-${range.month}.csv"
        is ExportRange.Custom -> "FixMoney-${range.fromIso}-to-${range.toIso}.csv"
    }

    internal fun buildCsv(expenses: List<ExpenseEntity>): String = buildString {
        append("Date,Category,Amount,Currency,Note\n")
        expenses.forEach { e ->
            append(csvField(e.date)).append(',')
            append(csvField(e.categoryName)).append(',')
            append(csvField(e.amount)).append(',')
            append(csvField(e.currency)).append(',')
            append(csvField(e.note)).append('\n')
        }
    }

    /**
     * Standard CSV escaping - quote when the value holds a comma, quote or
     * newline, and double up internal quotes. Notes are free text, so all three
     * are reachable; carried over from the old exporter unchanged.
     */
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}

/**
 * Writes the finished CSV somewhere the user can get at it, and returns a short
 * human description of where that was.
 *
 * The two platforms disagree completely about what "somewhere" means, which is
 * why this is the only part that is `expect`/`actual`: Android has a shared
 * Downloads collection to write into, iOS has no such place and hands the file
 * to a share sheet instead.
 */
expect class ExportFileWriter {
    suspend fun write(fileName: String, csv: String): ExportLocation

    /**
     * Hands the written file to whatever app can open a CSV, through the
     * platform's own chooser. Returns false when the platform cannot - the UI
     * then simply does not offer the action rather than failing on a tap.
     */
    fun open(location: ExportLocation): Boolean
}
