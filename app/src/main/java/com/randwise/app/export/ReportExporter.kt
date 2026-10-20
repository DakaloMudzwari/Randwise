package com.randwise.app.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.randwise.app.core.Money
import com.randwise.app.domain.Analysis
import com.randwise.app.domain.FinanceSnapshot
import java.io.OutputStream
import java.time.LocalDate

/** Writes a one-cycle summary report as a PDF using the platform PdfDocument API. */
object ReportExporter {

    fun writePdf(out: OutputStream, s: FinanceSnapshot, a: Analysis) {
        val doc = PdfDocument()
        val width = 595
        val height = 842
        val margin = 48f
        val title = Paint().apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF0B1F3A.toInt() }
        val head = Paint().apply { textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF059669.toInt() }
        val body = Paint().apply { textSize = 10.5f; color = 0xFF1F2937.toInt() }

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
        var y = margin
        fun line(text: String, paint: Paint = body, gap: Float = 15f) {
            if (y + gap > height - margin) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
                y = margin
            }
            page.canvas.drawText(text, margin, y, paint)
            y += gap
        }

        line("RandWise — Financial Summary", title, 28f)
        line("Generated ${LocalDate.now()} · Pay cycle ${a.cycle.start} to ${a.cycle.end}", body, 24f)

        line("Overview", head, 20f)
        line("Available balance: ${Money.format(a.liquidBalance)}")
        line("Safe-to-spend until payday: ${Money.format(a.safeToSpend.safeToSpend)} (${a.safeToSpend.daysUntilPayday} days)")
        line("Income this cycle: ${Money.format(a.cycleIncome)}")
        line("Spend this cycle: ${Money.format(a.cycleSpend)} · projected ${Money.format(a.projectedCycleSpend)}", gap = 24f)

        line("Accounts", head, 20f)
        a.accountBalances.forEach { line("${it.account.name} (${it.account.type.name.lowercase()}): ${Money.format(it.balance)}") }
        y += 9f

        line("Spending by category", head, 20f)
        a.categorySpend.forEach { line("${it.category?.name ?: "Uncategorised"}: ${Money.format(it.amount)}") }
        y += 9f

        if (a.budgetLines.isNotEmpty()) {
            line("Budgets", head, 20f)
            a.budgetLines.forEach {
                line("${it.category.name}: spent ${Money.format(it.spent)} + committed ${Money.format(it.committed)} of ${Money.format(it.limit)} — ${it.status.name.lowercase().replace('_', ' ')}")
            }
            y += 9f
        }

        line("Bills this cycle", head, 20f)
        a.occurrences.forEach { line("${it.dueDate} ${it.bill.name}: ${Money.format(it.bill.expectedAmountMinor)} ${if (it.paid) "(paid)" else "(due)"}") }
        y += 9f

        line("Insights", head, 20f)
        a.behavior.insights.forEach { line("• ${it.title}: ${it.body}".take(110)) }
        y += 9f

        line("Transactions this cycle", head, 20f)
        val cats = s.categories.associateBy { it.id }
        s.transactions.filter { a.cycle.containsEpochDay(it.date) }.sortedBy { it.date }.forEach { t ->
            val cat = t.categoryId?.let { cats[it]?.name } ?: ""
            line("${LocalDate.ofEpochDay(t.date)}  ${t.merchant.take(40).padEnd(40)}  ${Money.format(t.signedAmountMinor).padStart(16)}  $cat")
        }
        y += 12f
        line("Calculated on-device with integer cent arithmetic. General guidance only, not financial advice.", body)

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}
