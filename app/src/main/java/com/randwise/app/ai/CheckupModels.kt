package com.randwise.app.ai

import kotlinx.serialization.Serializable

/*
 * Money check-up: wire format shared with the gateway (backend/RandWise.Gateway/CheckupModels.cs) and the
 * saved result. Amounts are integer cents per month. The app computes every figure; Gemini only explains
 * them and may adjust budget limits, which the app then checks (BudgetPlanner.accept).
 */

@Serializable
data class CategoryTrendFact(
    val category: String,
    val essential: Boolean,
    /** Oldest first; zero for months before the user's history starts. */
    val monthsMinor: List<Long>,
    val typicalMinor: Long,
    val suggestedLimitMinor: Long? = null,
    val suggestedSavingMinor: Long? = null,
)

@Serializable
data class MerchantFact(val label: String, val totalMinor: Long, val visits: Int, val category: String? = null)

@Serializable
data class CheckupFacts(
    val currency: String,
    val today: String,
    val monthsCovered: Int,
    val daysOfData: Int,
    val monthlyIncomeMinor: Long,
    val plannedSavingsMinor: Long,
    val availableForBudgetMinor: Long,
    val monthlySpendMinor: List<Long>,
    val typicalSpendMinor: Long,
    val uncategorisedTypicalMinor: Long,
    val categories: List<CategoryTrendFact>,
    val merchants: List<MerchantFact>,
    val feeCount: Int,
    val feeTotalMinor: Long,
    val detectedInsights: List<String>,
)

@Serializable
data class CheckupRequest(val facts: CheckupFacts)

@Serializable
data class CheckupNote(val title: String, val body: String)

@Serializable
data class CheckupBudgetProposal(val category: String, val limitMinor: Long, val reason: String = "")

@Serializable
data class CheckupResponse(
    val headline: String,
    val summary: String,
    val insights: List<CheckupNote> = emptyList(),
    val suggestions: List<CheckupNote> = emptyList(),
    val budget: List<CheckupBudgetProposal> = emptyList(),
    val disclaimer: String = "",
)

// ---- Saved result (kept as JSON in the encrypted database)

@Serializable
data class CheckupCategory(val categoryId: Long, val name: String, val colorArgb: Long, val essential: Boolean, val monthsMinor: List<Long>, val typicalMinor: Long)

@Serializable
data class CheckupMerchant(val label: String, val totalMinor: Long, val visits: Int)

@Serializable
data class CheckupLine(val categoryId: Long, val category: String, val essential: Boolean, val typicalMinor: Long, val limitMinor: Long, val reason: String)

@Serializable
data class Checkup(
    val createdAt: Long,
    /** GEMINI or LOCAL. */
    val source: String,
    val note: String? = null,
    /** Newest transaction id when this ran; a higher one means there is new data to look at. */
    val lastTransactionId: Long,
    val daysOfData: Int,
    val monthsCovered: Int,
    val monthlyIncomeMinor: Long,
    val typicalSpendMinor: Long,
    val availableMinor: Long,
    val headline: String,
    val summary: String,
    val insights: List<CheckupNote>,
    val suggestions: List<CheckupNote>,
    val categories: List<CheckupCategory>,
    val merchants: List<CheckupMerchant>,
    val budget: List<CheckupLine>,
)
