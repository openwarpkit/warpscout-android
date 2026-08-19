package io.github.openwarpkit.warpscout.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import java.io.OutputStream

data class ReportImageColumn(
    val label: String,
    val width: Int
)

enum class ReportImageRowStyle {
    Working,
    TornDown,
    Failed
}

data class ReportImageRow(
    val values: List<String>,
    val style: ReportImageRowStyle
)

data class ReportImageDocument(
    val historyId: Long,
    val title: String,
    val metadata: List<Pair<String, String>>,
    val columns: List<ReportImageColumn>,
    val rows: List<ReportImageRow>
)

object ReportImageRenderer {
    private const val OuterPadding = 32f
    private const val TitleHeight = 56f
    private const val MetadataRowHeight = 28f
    private const val HeaderHeight = 54f
    private const val RowHeight = 34f
    private const val CellPadding = 10f

    fun write(document: ReportImageDocument, output: OutputStream) {
        require(document.columns.isNotEmpty())
        require(document.rows.all { it.values.size == document.columns.size })

        val tableWidth = document.columns.sumOf { it.width }
        val width = maxOf(720, tableWidth + (OuterPadding * 2).toInt())
        val metadataHeight = document.metadata.size * MetadataRowHeight
        val tableTop = OuterPadding + TitleHeight + metadataHeight + 24f
        val height = (tableTop + HeaderHeight + document.rows.size * RowHeight + OuterPadding).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(16, 19, 24))
            drawHeader(canvas, document)
            drawTable(canvas, document, tableTop)
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawHeader(canvas: Canvas, document: ReportImageDocument) {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 229, 234)
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(document.title, OuterPadding, OuterPadding + 34f, titlePaint)

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(169, 199, 255)
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(194, 199, 208)
            textSize = 18f
            typeface = Typeface.MONOSPACE
        }
        val labelWidth = document.metadata.maxOfOrNull { labelPaint.measureText("${it.first}:") } ?: 0f
        document.metadata.forEachIndexed { index, entry ->
            val baseline = OuterPadding + TitleHeight + index * MetadataRowHeight + 19f
            canvas.drawText("${entry.first}:", OuterPadding, baseline, labelPaint)
            canvas.drawText(entry.second, OuterPadding + labelWidth + 16f, baseline, valuePaint)
        }
    }

    private fun drawTable(canvas: Canvas, document: ReportImageDocument, tableTop: Float) {
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 229, 234)
            textSize = 17f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 229, 234)
            textSize = 17f
            typeface = Typeface.MONOSPACE
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(68, 75, 85)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val headerBackground = Paint().apply { color = Color.rgb(37, 42, 53) }
        val workingBackground = Paint().apply { color = Color.rgb(23, 42, 36) }
        val tornDownBackground = Paint().apply { color = Color.rgb(44, 39, 24) }
        val failedBackground = Paint().apply { color = Color.rgb(42, 27, 27) }

        var left = OuterPadding
        document.columns.forEach { column ->
            val right = left + column.width
            val rect = RectF(left, tableTop, right, tableTop + HeaderHeight)
            canvas.drawRect(rect, headerBackground)
            canvas.drawRect(rect, gridPaint)
            drawCellText(canvas, column.label, rect, headerPaint)
            left = right
        }

        document.rows.forEachIndexed { rowIndex, row ->
            val top = tableTop + HeaderHeight + rowIndex * RowHeight
            val background = when (row.style) {
                ReportImageRowStyle.Working -> workingBackground
                ReportImageRowStyle.TornDown -> tornDownBackground
                ReportImageRowStyle.Failed -> failedBackground
            }
            left = OuterPadding
            row.values.forEachIndexed { columnIndex, value ->
                val right = left + document.columns[columnIndex].width
                val rect = RectF(left, top, right, top + RowHeight)
                canvas.drawRect(rect, background)
                canvas.drawRect(rect, gridPaint)
                drawCellText(canvas, value, rect, valuePaint)
                left = right
            }
        }
    }

    private fun drawCellText(canvas: Canvas, value: String, bounds: RectF, paint: TextPaint) {
        val availableWidth = bounds.width() - CellPadding * 2
        val text = TextUtils.ellipsize(value, paint, availableWidth, TextUtils.TruncateAt.END).toString()
        val metrics = paint.fontMetrics
        val baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, bounds.left + CellPadding, baseline, paint)
    }
}
