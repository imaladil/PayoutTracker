package com.payouttracker.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A4 page in PDF points
private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 36f
private const val BOTTOM = PAGE_H - 48f   // keeps space for the footer

// The PDF is printed on white paper, so it uses dark text instead of the app's black theme
private val BRAND = Color.rgb(34, 139, 34)
private val DARK = Color.rgb(25, 25, 25)
private val MUTED = Color.rgb(110, 110, 110)
private val POSITIVE = Color.rgb(27, 127, 59)
private val NEGATIVE = Color.rgb(198, 40, 40)
private val RULE = Color.rgb(222, 222, 222)
private val BAND = Color.rgb(242, 242, 242)

// Right edges of the table columns (the "#" and "Time" columns are left aligned)
private const val COL_NO = 36f
private const val COL_TIME = 62f
private const val COL_STAKE = 190f
private const val COL_ODDS = 240f
private const val COL_PROFIT = 335f
private const val COL_PAID = 440f
private const val COL_PROGRESS = 559f

private fun paint(
    size: Float,
    bold: Boolean = false,
    color: Int = DARK,
    align: Paint.Align = Paint.Align.LEFT
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    this.color = color
    textAlign = align
}

private fun signedFull(v: Double): String = (if (v > 0) "+" else "") + fmt(v)

/** Keeps track of the current page and the current vertical position while drawing. */
private class PdfPager(private val doc: PdfDocument) {
    private var page: PdfDocument.Page? = null
    private var pageNo = 0
    lateinit var canvas: Canvas
    var y = MARGIN

    init {
        newPage()
    }

    fun newPage() {
        finish()
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        page = p
        canvas = p.canvas
        y = MARGIN
    }

    /** Starts a new page when [height] does not fit any more. Returns true if it did. */
    fun ensure(height: Float): Boolean {
        if (y + height > BOTTOM) {
            newPage()
            return true
        }
        return false
    }

    fun finish() {
        val p = page ?: return
        canvas.drawText("Payout Tracker  |  Page $pageNo", MARGIN, PAGE_H - 22f, paint(9f, false, MUTED))
        doc.finishPage(p)
        page = null
    }
}

private fun drawTableHeader(pg: PdfPager) {
    pg.canvas.drawRect(MARGIN, pg.y - 11f, PAGE_W - MARGIN, pg.y + 5f, Paint().apply { color = BAND })
    val h = paint(9f, true, MUTED)
    val hr = paint(9f, true, MUTED, Paint.Align.RIGHT)
    pg.canvas.drawText("#", COL_NO + 2f, pg.y, h)
    pg.canvas.drawText("Time", COL_TIME, pg.y, h)
    pg.canvas.drawText("Stake", COL_STAKE, pg.y, hr)
    pg.canvas.drawText("Odds", COL_ODDS, pg.y, hr)
    pg.canvas.drawText("Profit", COL_PROFIT, pg.y, hr)
    pg.canvas.drawText("Paid Out", COL_PAID, pg.y, hr)
    pg.canvas.drawText("Target %", COL_PROGRESS, pg.y, hr)
    pg.y += 18f
}

private fun drawRow(pg: PdfPager, r: ChainRow) {
    val left = paint(10f)
    val right = paint(10f, false, DARK, Paint.Align.RIGHT)
    pg.canvas.drawText("#${r.no}", COL_NO + 2f, pg.y, paint(10f, true, BRAND))
    pg.canvas.drawText(timeText(r.time), COL_TIME, pg.y, left)
    pg.canvas.drawText(fmt(r.stake), COL_STAKE, pg.y, right)
    pg.canvas.drawText(plain(r.odds), COL_ODDS, pg.y, right)
    pg.canvas.drawText(
        signedFull(r.profit), COL_PROFIT, pg.y,
        paint(10f, false, if (r.profit >= 0) POSITIVE else NEGATIVE, Paint.Align.RIGHT)
    )
    pg.canvas.drawText(fmt(r.paidOut), COL_PAID, pg.y, paint(10f, true, DARK, Paint.Align.RIGHT))
    pg.canvas.drawText(pct(r.progress * 100), COL_PROGRESS, pg.y, right)
    pg.canvas.drawLine(MARGIN, pg.y + 5f, PAGE_W - MARGIN, pg.y + 5f, Paint().apply { color = RULE })
    pg.y += 16f
}

/**
 * Writes the Brief Report of one Payout Target as a PDF: target details, overall averages and
 * a section for every day with each stake's time, stake, odds, profit, paid out and target progress.
 */
fun writeReportPdf(target: TargetData, out: OutputStream) {
    val rows = target.rows()
    val days = rows.groupBy { dayKey(it.time) }.values.toList()
    val doc = PdfDocument()
    try {
        val pg = PdfPager(doc)

        // ---- Header band ----
        pg.canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 78f, Paint().apply { color = BRAND })
        pg.canvas.drawText("Payout Tracker", MARGIN, 36f, paint(22f, true, Color.WHITE))
        pg.canvas.drawText("Brief Report", MARGIN, 58f, paint(13f, false, Color.WHITE))
        val generated = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())
        pg.canvas.drawText(
            "Generated $generated", PAGE_W - MARGIN, 58f,
            paint(10f, false, Color.WHITE, Paint.Align.RIGHT)
        )
        pg.y = 110f

        // ---- Target title and status ----
        pg.canvas.drawText(target.name, MARGIN, pg.y, paint(18f, true))
        pg.y += 20f
        val over = target.exceeded
        val status = "Target Fulfilled" + if (over > 0) "  |  crossed by " + fmt(over) else ""
        pg.canvas.drawText(status, MARGIN, pg.y, paint(12f, true, POSITIVE))
        pg.y += 28f

        // ---- Key figures, two per row ----
        val avgStake = avgOf(rows.map { it.stake }) ?: 0.0
        val avgOdds = avgOf(rows.map { it.odds }) ?: 0.0
        val avgProfit = avgOf(rows.map { it.profit }) ?: 0.0
        val perDay = rows.size.toDouble() / days.size
        val facts = listOf(
            Triple("Target", fmt(target.amount), DARK),
            Triple("Final Payout", fmt(target.balance), DARK),
            Triple("Opened", dateTimeText(target.openedAt), DARK),
            Triple("Completed", dateTimeText(rows.last().time), DARK),
            Triple("First Stake", fmt(target.firstStake ?: 0.0), DARK),
            Triple("Total Profit", signedFull(target.profit), if (target.profit >= 0) POSITIVE else NEGATIVE),
            Triple("Total Stakes", rows.size.toString(), DARK),
            Triple("Active Days", days.size.toString(), DARK),
            Triple("Average Stake", fmt(avgStake), DARK),
            Triple("Average Odds", plain(avgOdds), DARK),
            Triple("Average Profit", signedFull(avgProfit), if (avgProfit >= 0) POSITIVE else NEGATIVE),
            Triple("Stakes per Day", String.format(Locale.US, "%.1f", perDay), DARK)
        )
        facts.forEachIndexed { i, f ->
            val x = if (i % 2 == 0) MARGIN else 310f
            pg.canvas.drawText(f.first, x, pg.y, paint(9f, false, MUTED))
            pg.canvas.drawText(f.second, x, pg.y + 15f, paint(12f, true, f.third))
            if (i % 2 == 1) pg.y += 38f
        }
        pg.y += 6f
        pg.canvas.drawLine(MARGIN, pg.y, PAGE_W - MARGIN, pg.y, Paint().apply { color = RULE })
        pg.y += 26f

        // ---- Daily progress ----
        pg.canvas.drawText("Daily Progress", MARGIN, pg.y, paint(15f, true))
        pg.y += 26f

        for (day in days) {
            pg.ensure(90f)   // day heading, table header and at least one row stay together
            pg.canvas.drawText(dayLabel(day.first().time), MARGIN, pg.y, paint(13f, true, BRAND))
            pg.canvas.drawText(
                if (day.size == 1) "1 stake" else "${day.size} stakes",
                PAGE_W - MARGIN, pg.y, paint(11f, true, DARK, Paint.Align.RIGHT)
            )
            pg.y += 15f
            val dStake = avgOf(day.map { it.stake }) ?: 0.0
            val dOdds = avgOf(day.map { it.odds }) ?: 0.0
            val dProfit = avgOf(day.map { it.profit }) ?: 0.0
            pg.canvas.drawText(
                "Avg stake " + fmt(dStake) + "  |  Avg odds " + plain(dOdds) + "  |  Avg profit " + signedFull(dProfit),
                MARGIN, pg.y, paint(9.5f, false, MUTED)
            )
            pg.y += 18f
            drawTableHeader(pg)
            for (r in day) {
                if (pg.ensure(18f)) drawTableHeader(pg)
                drawRow(pg, r)
            }
            pg.y += 16f
        }

        pg.finish()
        doc.writeTo(out)
    } finally {
        doc.close()
    }
}
