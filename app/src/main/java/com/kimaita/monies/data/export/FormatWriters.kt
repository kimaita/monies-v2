package com.kimaita.monies.data.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.kimaita.monies.models.export.ExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

interface FormatWriter {
    val format: ExportFormat
    suspend fun write(out: OutputStream, pages: TransactionPages, onProgress: suspend (Int) -> Unit)
}

class CsvWriter @Inject constructor() : FormatWriter {
    override val format = ExportFormat.CSV

    override suspend fun write(
        out: OutputStream,
        pages: TransactionPages,
        onProgress: suspend (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val w = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8))
        w.write("\uFEFF")
        w.write("Code,Amount,Cost,Date,Time,Subject Name,Subject Number,Type\r\n")
        val sb = StringBuilder(256)
        var count = 0
        pages.forEach { page ->
            for (t in page) {
                sb.setLength(0)
                field(sb, t.transaction.transactionCode ?: ""); sb.append(',')
                field(sb, t.transaction.amount.toString()); sb.append(',')
                field(sb, t.transaction.cost?.toString() ?: "0.0"); sb.append(',')
                field(sb, t.transaction.transactionTime.toLocalDate()?.toString() ?: ""); sb.append(
                    ','
                )
                field(sb, t.transaction.transactionTime.toLocalTime()?.toString() ?: ""); sb.append(
                    ','
                )
                field(sb, t.subject?.name ?: ""); sb.append(',')
                field(sb, t.subject?.number ?: ""); sb.append(',')
                field(sb, t.type.displayName ?: "")
                sb.append("\r\n")
                w.write(sb.toString())
                count++
            }
            onProgress(count)
        }
        w.flush()
    }

    private fun field(sb: StringBuilder, v: String) {
        sb.append('"')
        for (c in v) {
            if (c == '"') sb.append('"'); sb.append(c)
        }
        sb.append('"')
    }
}

class JsonWriter @Inject constructor() : FormatWriter {
    override val format = ExportFormat.JSON

    override suspend fun write(
        out: OutputStream,
        pages: TransactionPages,
        onProgress: suspend (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val jw = android.util.JsonWriter(OutputStreamWriter(out, Charsets.UTF_8))
        jw.setIndent("  ")
        jw.beginArray()
        var count = 0
        pages.forEach { page ->
            for (t in page) {
                jw.beginObject()
                jw.name("code").value(t.transaction.transactionCode)
                jw.name("amount").value(t.transaction.amount)
                jw.name("cost").value(t.transaction.cost)
                jw.name("date").value(t.transaction.transactionTime.toLocalDate()?.toString())
                jw.name("time").value(t.transaction.transactionTime.toLocalTime()?.toString())
                jw.name("subjectName").value(t.subject?.name)
                jw.name("subjectNumber").value(t.subject?.number)
                jw.name("type").value(t.type.displayName)
                jw.endObject()
                count++
            }
            onProgress(count)
        }
        jw.endArray()
        jw.flush()
    }
}

class XlsxWriter @Inject constructor() : FormatWriter {
    override val format = ExportFormat.XLSX

    override suspend fun write(
        out: OutputStream,
        pages: TransactionPages,
        onProgress: suspend (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val zip = ZipOutputStream(out)
        zip.setLevel(Deflater.BEST_SPEED)

        fun part(name: String, body: String) {
            zip.putNextEntry(ZipEntry(name));
            zip.write(body.toByteArray(Charsets.UTF_8));
            zip.closeEntry()
        }
        part("[Content_Types].xml", XLSX_CONTENT_TYPES)
        part("_rels/.rels", XLSX_ROOT_RELS)
        part("xl/workbook.xml", XLSX_WORKBOOK)
        part("xl/_rels/workbook.xml.rels", XLSX_WORKBOOK_RELS)

        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        val w = BufferedWriter(
            OutputStreamWriter(
                zip, Charsets.UTF_8
            )
        )
        w.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        w.write("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

        val sb = StringBuilder(512)
        var row = 1
        sb.append("<row r=\"").append(row).append("\">")
        listOf("Code", "Amount", "Cost", "Date", "Time", "Subject Name", "Subject Number", "Type")
            .forEachIndexed { i, v -> inlineCell(sb, colRef(i, row), v) }
        sb.append("</row>"); w.write(sb.toString()); row++

        var count = 0
        pages.forEach { page ->
            for (t in page) {
                sb.setLength(0)
                sb.append("<row r=\"").append(row).append("\">")
                inlineCell(sb, colRef(0, row), t.transaction.transactionCode ?: "")
                numCell(sb, colRef(1, row), t.transaction.amount)
                numCell(sb, colRef(2, row), t.transaction.cost)
                inlineCell(
                    sb,
                    colRef(3, row),
                    t.transaction.transactionTime.toLocalDate()?.toString() ?: ""
                )
                inlineCell(
                    sb,
                    colRef(4, row),
                    t.transaction.transactionTime.toLocalTime()?.toString() ?: ""
                )
                inlineCell(sb, colRef(5, row), t.subject?.name ?: "")
                inlineCell(sb, colRef(6, row), t.subject?.number ?: "")
                inlineCell(sb, colRef(7, row), t.type.displayName ?: "")
                sb.append("</row>")
                w.write(sb.toString())
                row++; count++
            }
            onProgress(count)
        }
        w.write("</sheetData></worksheet>"); w.flush()
        zip.closeEntry()
        zip.finish()
    }
}

class PdfWriter @Inject constructor() : FormatWriter {
    override val format = ExportFormat.PDF

    override suspend fun write(
        out: OutputStream, pages: TransactionPages, onProgress: suspend (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val pw = 595;
        val ph = 842;
        val margin = 36f;
        val rowH = 16f;
        val gap = 4f
        val usable = pw - 2 * margin

        val headers = listOf(
            "Code",
            "Amount",
            "Cost",
            "Date",
            "Time",
            "Subject Name",
            "Subject Number",
            "Type"
        )
        val weights =
            floatArrayOf(0.11f, 0.10f, 0.10f, 0.12f, 0.10f, 0.19f, 0.15f, 0.13f)
        val widths = FloatArray(headers.size) { weights[it] * usable }
        val xs = FloatArray(headers.size)
        run {
            var acc = margin; for (i in headers.indices) {
            xs[i] = acc; acc += widths[i]
        }
        }

        val cell = Paint().apply { textSize = 9f; color = Color.DKGRAY; isAntiAlias = true }
        val head = Paint(cell).apply { isFakeBoldText = true; color = Color.BLACK }
        val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

        val pdf = PdfDocument()
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create())
        var canvas = page.canvas
        var y = margin

        fun drawRow(cells: List<String>, paint: Paint) {
            for (i in cells.indices) canvas.drawText(
                fit(cells[i], paint, widths[i] - gap),
                xs[i],
                y,
                paint
            )
            y += rowH
        }

        fun drawHeader() {
            drawRow(headers, head)
            canvas.drawLine(margin, y - rowH + 4f, pw - margin, y - rowH + 4f, rule)
            y += rowH * 0.4f
        }

        drawHeader()
        var count = 0
        pages.forEach { batch ->
            for (t in batch) {
                if (y + rowH > ph - margin) {
                    pdf.finishPage(page)
                    page = pdf.startPage(
                        PdfDocument.PageInfo.Builder(pw, ph, pdf.pages.size + 1).create()
                    )
                    canvas = page.canvas; y = margin; drawHeader()
                }
                drawRow(
                    listOf(
                        t.transaction.transactionCode ?: "",
                        t.transaction.amount.toString(),
                        t.transaction.cost?.toString() ?: "0.0",
                        t.transaction.transactionTime.toLocalDate()?.toString() ?: "",
                        t.transaction.transactionTime.toLocalTime()?.toString() ?: "",
                        t.subject?.name ?: "",
                        t.subject?.number ?: "",
                        t.type.displayName ?: "",
                    ),
                    cell,
                )
                count++
            }
            onProgress(count)
        }
        pdf.finishPage(page)
        pdf.writeTo(out)
        pdf.close()
    }

    /** Truncate with an ellipsis to fit a column width (measured, so no overlap regardless of font). */
    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellW = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ellW > maxWidth) end--
        return text.substring(0, end) + ellipsis
    }
}

private fun inlineCell(sb: StringBuilder, ref: String, value: String) {
    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
    xmlEscape(sb, value)
    sb.append("</t></is></c>")
}

private fun numCell(sb: StringBuilder, ref: String, value: Number?) {
    sb.append("<c r=\"").append(ref).append("\"><v>").append((value ?: 0).toString())
        .append("</v></c>")
}

private fun colRef(col: Int, row: Int): String {
    var n = col;
    val s = StringBuilder()
    do {
        s.insert(0, 'A' + (n % 26)); n = n / 26 - 1
    } while (n >= 0)
    return s.append(row).toString()
}

private fun xmlEscape(sb: StringBuilder, s: String) {
    for (c in s) when (c) {
        '&' -> sb.append("&amp;"); '<' -> sb.append("&lt;"); '>' -> sb.append("&gt;")
        '"' -> sb.append("&quot;"); '\'' -> sb.append("&apos;")
        else -> sb.append(if (c.code < 0x20 && c != '\t' && c != '\n' && c != '\r') ' ' else c)
    }
}

private const val XLSX_CONTENT_TYPES =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
private const val XLSX_ROOT_RELS =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
private const val XLSX_WORKBOOK =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Transactions" sheetId="1" r:id="rId1"/></sheets></workbook>"""
private const val XLSX_WORKBOOK_RELS =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""