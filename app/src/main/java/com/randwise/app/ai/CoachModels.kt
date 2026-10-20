package com.randwise.app.ai

import kotlinx.serialization.Serializable

/*
 * Wire format shared with the ASP.NET Core gateway (backend/RandWise.Gateway).
 * All amounts are integer cents; the gateway and model never compute balances.
 */

@Serializable
data class CategoryFact(val category: String, val spentMinor: Long, val limitMinor: Long? = null, val projectedMinor: Long? = null, val status: String? = null)

@Serializable
data class BillFact(val label: String, val amountMinor: Long, val dueInDays: Long, val paid: Boolean)

@Serializable
data class DebtFact(val label: String, val balanceMinor: Long, val aprPercent: String, val minPaymentMinor: Long)

@Serializable
data class CoachFacts(
    val currency: String,
    val today: String,
    val daysUntilPayday: Long,
    val liquidBalanceMinor: Long,
    val safeToSpendMinor: Long,
    val safeToSpendPerDayMinor: Long,
    val safetyBufferMinor: Long,
    val monthlyIncomeMinor: Long,
    val cycleSpendMinor: Long,
    val projectedCycleSpendMinor: Long,
    val categories: List<CategoryFact>,
    val bills: List<BillFact>,
    val forecastLowestMinor: Long,
    val forecastLowestInDays: Long,
    val forecastNegativeInDays: Long? = null,
    val volatilityScore: Int,
    val postPaydaySpikePercent: String,
    val feeTrapCount: Int,
    val feeTrapTotalMinor: Long,
    val emergencyMonthsCovered: String,
    val emergencyTargetMonths: Int,
    val debts: List<DebtFact>,
    val detectedInsights: List<String>,
)

@Serializable
data class ChatTurn(val role: String, val text: String)

@Serializable
data class CoachRequest(val question: String, val facts: CoachFacts, val history: List<ChatTurn> = emptyList())

@Serializable
data class CoachResponse(
    val answer: String,
    val highlights: List<String> = emptyList(),
    val disclaimer: String = "",
)
