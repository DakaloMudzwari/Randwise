package com.randwise.app

import com.randwise.app.data.model.BillFrequency
import com.randwise.app.data.model.PayFrequency
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.UserProfile
import com.randwise.app.domain.FinanceAnalyzer
import com.randwise.app.domain.PayCycle
import com.randwise.app.domain.PaySchedule
import com.randwise.app.domain.engine.CashFlowForecaster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PayFrequencyTest {
    private val today = LocalDate.of(2026, 10, 19) // Monday

    @Test
    fun `weekly cycle runs Friday to Friday from the anchor`() {
        val c = PayCycle.forDate(today, PaySchedule(PayFrequency.WEEKLY, anchor = LocalDate.of(2026, 10, 16)))
        assertEquals(LocalDate.of(2026, 10, 16), c.start)
        assertEquals(LocalDate.of(2026, 10, 23), c.nextPayday)
        assertEquals(7, c.totalDays)
        assertEquals(LocalDate.of(2026, 10, 9), c.previous().start)
    }

    @Test
    fun `fortnightly cycle works whether the anchor is in the past or future`() {
        val past = PayCycle.forDate(today, PaySchedule(PayFrequency.FORTNIGHTLY, anchor = LocalDate.of(2026, 10, 2)))
        val future = PayCycle.forDate(today, PaySchedule(PayFrequency.FORTNIGHTLY, anchor = LocalDate.of(2026, 10, 30)))
        assertEquals(LocalDate.of(2026, 10, 16), past.start)
        assertEquals(LocalDate.of(2026, 10, 30), past.nextPayday)
        assertEquals(past, future.copy(schedule = past.schedule))
    }

    @Test
    fun `paydays between dates`() {
        val weekly = PaySchedule(PayFrequency.WEEKLY, anchor = LocalDate.of(2026, 10, 16))
        assertEquals(
            listOf(23, 30, 6, 13),
            PayCycle.paydaysBetween(today, LocalDate.of(2026, 11, 19), weekly).map { it.dayOfMonth },
        )
        val monthly = PaySchedule(PayFrequency.MONTHLY, 25, adjustForWeekend = true)
        assertEquals(listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 11, 25)), PayCycle.paydaysBetween(today, LocalDate.of(2026, 12, 1), monthly))
    }

    @Test
    fun `weekly earners get every pay in the forecast`() {
        val profile = UserProfile(payday = 1, netPayMinor = 450_000, safetyBufferMinor = 0, payFrequency = PayFrequency.WEEKLY, payAnchorDate = LocalDate.of(2026, 10, 16).toEpochDay())
        val events = CashFlowForecaster.scheduledEvents(today, 30, profile, emptyList(), emptyList())
        assertEquals(4, events.count { it.label == "Salary" }) // 23, 30 Oct; 6, 13 Nov (horizon ends 18 Nov)
        assertEquals(450_000L, events.first().signedAmount)
    }
}

class BillFrequencyTest {
    private fun bill(f: BillFrequency, anchor: LocalDate? = null, dueDay: Int = 1) =
        RecurringBill(id = 1, name = "b", merchantPattern = "B", expectedAmountMinor = 10_000, dueDay = dueDay, isConfirmed = true, frequency = f, anchorDate = anchor?.toEpochDay())

    @Test
    fun `weekly bills repeat every 7 days from their anchor`() {
        val dates = PayCycle.dueDatesBetween(bill(BillFrequency.WEEKLY, LocalDate.of(2026, 10, 5)), LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 31))
        assertEquals(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26)), dates)
    }

    @Test
    fun `yearly bills land once a year and survive leap days`() {
        val leap = bill(BillFrequency.YEARLY, LocalDate.of(2024, 2, 29))
        assertEquals(listOf(LocalDate.of(2026, 2, 28)), PayCycle.dueDatesBetween(leap, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `monthly bills clamp to short months`() {
        assertEquals(listOf(LocalDate.of(2027, 2, 28)), PayCycle.dueDatesBetween(bill(BillFrequency.MONTHLY, dueDay = 31), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28)))
    }

    @Test
    fun `monthly equivalents`() {
        assertEquals(43_333, FinanceAnalyzer.monthlyEquivalent(bill(BillFrequency.WEEKLY, LocalDate.now())))
        assertEquals(833, FinanceAnalyzer.monthlyEquivalent(bill(BillFrequency.YEARLY, LocalDate.now())))
        assertEquals(10_000, FinanceAnalyzer.monthlyEquivalent(bill(BillFrequency.MONTHLY)))
    }
}
