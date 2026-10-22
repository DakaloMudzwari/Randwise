package com.randwise.app.data

/**
 * The in-app privacy notice (POPIA section 18 "notification to data subject"). Bump [VERSION]
 * whenever the text changes — consent records store the version the user actually saw.
 * Have it reviewed by your Information Officer / legal adviser before release.
 */
object Privacy {
    const val VERSION = "2026-10.2"

    object Consent {
        const val STATEMENTS = "STATEMENT_PROCESSING"
        const val AI = "AI_INTERPRETATION_OFFSHORE"
    }

    data class Section(val title: String, val body: String)

    val sections = listOf(
        Section(
            "What RandWise stores",
            "Your financial profile, accounts (nicknames only, never account numbers), transactions, budgets, bills, goals and debts. " +
                "Everything is stored only on this phone in an encrypted database (SQLCipher, key protected by the Android Keystore).",
        ),
        Section(
            "Bank statements",
            "Statements you choose are read on this phone to extract dates, descriptions and amounts. The file itself is not kept and is never uploaded. " +
                "This only happens after you consent.",
        ),
        Section(
            "AI coach and cross-border transfer",
            "If you switch on AI interpretation, RandWise sends anonymised totals — such as safe-to-spend, category totals and bill amounts — " +
                "to the RandWise gateway, which forwards them to Google's Gemini service. Google may process this data on servers outside South Africa. " +
                "The money check-up also sends three months of category totals and your most-used shops (first word of the name only, e.g. \"Woolworths\"). " +
                "No names, account numbers, statement lines or notes are sent. With AI interpretation off, the coach and check-up run entirely on your phone.",
        ),
        Section(
            "Advertising",
            "The free version shows Google AdMob ads. Google may use device identifiers to serve ads, subject to the consent choices shown by Google. " +
                "Ads never receive your financial data.",
        ),
        Section(
            "Your rights",
            "You can withdraw any consent at any time in the Vault, export all your data (CSV/PDF), and permanently erase everything with one tap. " +
                "Every consent you give or withdraw is recorded with a date and the notice version, and you can view that history in the Vault.",
        ),
        Section(
            "Not financial advice",
            "RandWise provides factual information calculated from your own data. It is not a licensed financial services provider and nothing in the app " +
                "is financial advice as defined in the FAIS Act. Speak to a registered adviser before major financial decisions.",
        ),
    )
}
