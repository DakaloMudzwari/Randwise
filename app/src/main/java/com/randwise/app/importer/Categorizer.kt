package com.randwise.app.importer

import com.randwise.app.data.model.Bucket
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.CategoryRule
import com.randwise.app.domain.MerchantText

/** Maps statement descriptions to categories: user rules first, then South African merchant keywords. */
object Categorizer {

    data class Seed(val name: String, val bucket: Bucket, val color: Long, val essential: Boolean)

    val DEFAULT_CATEGORIES = listOf(
        Seed("Housing", Bucket.FIXED, 0xFF1E3A8A, true),
        Seed("Utilities", Bucket.FIXED, 0xFF0891B2, true),
        Seed("Insurance", Bucket.FIXED, 0xFF6366F1, true),
        Seed("Education", Bucket.FIXED, 0xFF7C3AED, true),
        Seed("Groceries", Bucket.VARIABLE, 0xFF10B981, true),
        Seed("Transport", Bucket.VARIABLE, 0xFF0EA5E9, true),
        Seed("Airtime & Data", Bucket.VARIABLE, 0xFF14B8A6, true),
        Seed("Health", Bucket.VARIABLE, 0xFF22C55E, true),
        Seed("Eating Out", Bucket.VARIABLE, 0xFFF59E0B, false),
        Seed("Entertainment", Bucket.VARIABLE, 0xFFEC4899, false),
        Seed("Shopping", Bucket.VARIABLE, 0xFFF97316, false),
        Seed("Subscriptions", Bucket.VARIABLE, 0xFFA855F7, false),
        Seed("Personal Care", Bucket.VARIABLE, 0xFFD946EF, false),
        Seed("Bank Fees", Bucket.VARIABLE, 0xFFEF4444, false),
        Seed("Other", Bucket.VARIABLE, 0xFF64748B, false),
        Seed("Debt Repayments", Bucket.DEBT, 0xFFB91C1C, true),
        Seed("Savings", Bucket.SAVINGS, 0xFF059669, false),
        Seed("Salary", Bucket.INCOME, 0xFF047857, false),
        Seed("Other Income", Bucket.INCOME, 0xFF34D399, false),
    )

    private val keywords: Map<String, List<String>> = mapOf(
        "Groceries" to listOf("CHECKERS", "PICK N PAY", "PNP", "WOOLWORTHS", "WOOLIES", "SPAR", "SHOPRITE", "USAVE", "FOOD LOVER", "BOXER", "OK FOODS", "MAKRO", "SIXTY60"),
        "Transport" to listOf("UBER TRIP", "UBER BV", "BOLT", "ENGEN", "SHELL", "SASOL", "CALTEX", "TOTAL", "ASTRON", "BP ", "GAUTRAIN", "TOLL", "SANRAL", "TAXI", "PARKING", "MYCITI"),
        "Eating Out" to listOf("KFC", "NANDOS", "MCDONALDS", "STEERS", "WIMPY", "DEBONAIRS", "ROMANS", "SPUR", "OCEAN BASKET", "MR D", "UBER EATS", "STARBUCKS", "VIDA", "MUGG", "BURGER KING", "FISHAWAYS", "CHICKEN LICKEN"),
        "Utilities" to listOf("CITY OF", "ESKOM", "PREPAID ELEC", "MUNICIPAL", "ELECTRICITY", "WATER", "RATES"),
        "Airtime & Data" to listOf("VODACOM", "MTN", "CELL C", "TELKOM", "RAIN", "AIRTIME", "DATA BUNDLE", "AFRIHOST", "WEBAFRICA", "VUMA", "OPENSERVE"),
        "Subscriptions" to listOf("NETFLIX", "SHOWMAX", "SPOTIFY", "DSTV", "MULTICHOICE", "APPLE.COM", "APPLE COM", "GOOGLE", "YOUTUBE", "DISNEY", "AMAZON PRIME", "PRIME VIDEO", "MICROSOFT", "ICLOUD", "BOX OFFICE"),
        "Insurance" to listOf("DISCOVERY", "OLD MUTUAL", "SANLAM", "OUTSURANCE", "MIWAY", "KING PRICE", "SANTAM", "LIBERTY", "MOMENTUM", "HOLLARD", "BUDGET INS", "AVBOB", "CLIENTELE", "1LIFE", "DIAL DIRECT"),
        "Health" to listOf("CLICKS", "DIS-CHEM", "DISCHEM", "PHARMACY", "MEDICAL", "DOCTOR", "DR ", "HOSPITAL", "NETCARE", "MEDICLINIC", "LIFE HEALTHCARE", "PATHCARE", "LANCET"),
        "Shopping" to listOf("TAKEALOT", "MR PRICE", "MRP", "EDGARS", "TRUWORTHS", "FOSCHINI", "PEP", "ACKERMANS", "H&M", "ZARA", "SUPERBALIST", "GAME", "INCREDIBLE", "MAKRO", "BUILDERS", "LEROY", "CASH CRUSADERS", "SHEIN", "TEMU", "AMAZON"),
        "Entertainment" to listOf("STER KINEKOR", "NU METRO", "COMPUTICKET", "QUICKET", "BETWAY", "HOLLYWOODBETS", "SUPABETS", "LOTTO", "PLAYSTATION", "STEAM", "XBOX"),
        "Housing" to listOf("RENT", "BOND", "LEVY", "HOME LOAN", "BODY CORPORATE"),
        "Education" to listOf("SCHOOL", "UNIVERSITY", "UNISA", "COLLEGE", "TUITION", "CRECHE"),
        "Personal Care" to listOf("SALON", "BARBER", "SPA", "BEAUTY", "GYM", "VIRGIN ACTIVE", "PLANET FITNESS"),
        "Bank Fees" to listOf("FEE", "CHARGE", "OVERDRAFT", "UNPAID", "PENALTY", "INTEREST DEBIT", "SMS NOTIFICATION"),
        "Debt Repayments" to listOf("LOAN", "CREDIT CARD", "MICRO", "FINANCE", "WESBANK", "MFC", "DIRECT AXIS", "AFRICAN BANK", "RCS", "LAYBY"),
        "Salary" to listOf("SALARY", "SAL ", "PAYROLL", "WAGES"),
    )

    /** Whole-word matchers so "FEE" doesn't hit "COFFEE" and "RAIN" doesn't hit "TRAINING". */
    private val matchers: Map<String, List<Regex>> = keywords.mapValues { (_, words) ->
        words.map { Regex("(^|[^A-Z0-9])" + Regex.escape(it.trim()) + "([^A-Z0-9]|$)") }
    }

    fun categorize(description: String, isCredit: Boolean, rules: List<CategoryRule>, categories: List<Category>): Long? {
        val upper = description.uppercase()
        val normalized = MerchantText.normalize(description)
        rules.sortedByDescending { it.pattern.length }.firstOrNull { upper.contains(it.pattern) || normalized.contains(it.pattern) }
            ?.let { rule -> if (categories.any { it.id == rule.categoryId }) return rule.categoryId }
        val byName = categories.associateBy { it.name }
        if (isCredit) {
            val income = if (matchers.getValue("Salary").any { it.containsMatchIn(upper) }) "Salary" else "Other Income"
            return byName[income]?.id
        }
        if (MerchantText.isFeeTrap(description)) return byName["Bank Fees"]?.id
        for ((name, rx) in matchers) {
            if (name == "Salary") continue
            if (rx.any { it.containsMatchIn(upper) }) return byName[name]?.id
        }
        return null
    }
}
