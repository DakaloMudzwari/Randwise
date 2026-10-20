package com.randwise.app.domain

import com.randwise.app.data.model.Transaction

/**
 * Learning from the user's own corrections: when they recategorise one transaction, the same
 * merchant elsewhere in their history is probably the same kind of spend.
 */
object CategoryLearning {

    /** Pattern stored as a rule for future imports, or null when the merchant text is too vague. */
    fun patternFor(t: Transaction): String? = MerchantText.normalize(t.merchant).takeIf { it.length >= 3 }

    /**
     * Other transactions from the same merchant that would change if the correction were applied:
     * same direction (spend vs income), not internal transfers, not already in [categoryId].
     */
    fun similar(all: List<Transaction>, corrected: Transaction, categoryId: Long?): List<Transaction> {
        val pattern = patternFor(corrected) ?: return emptyList()
        if (categoryId == null) return emptyList()
        return all.filter {
            it.id != corrected.id &&
                !it.type.isInternal &&
                it.type.countsAsIncome == corrected.type.countsAsIncome &&
                it.categoryId != categoryId &&
                MerchantText.normalize(it.merchant) == pattern
        }
    }
}
