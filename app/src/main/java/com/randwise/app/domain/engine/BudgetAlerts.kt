package com.randwise.app.domain.engine

import com.randwise.app.core.Money

/** A budget notification to send, with a stable [key] so it's only ever sent once per cycle. */
data class BudgetAlert(val key: String, val title: String, val body: String)

/** Decides which budget thresholds have newly been crossed this pay cycle. */
object BudgetAlerts {
    const val WARN_BP = 8_000L

    fun due(lines: List<BudgetLine>, cycleStartEpochDay: Long, alreadySent: Set<String>): List<BudgetAlert> =
        lines.filter { it.limit > 0 }.mapNotNull { line ->
            val used = line.spent + line.committed
            val level = when {
                used > line.limit -> 100
                line.usedBp >= WARN_BP -> 80
                else -> return@mapNotNull null
            }
            val key = "${line.category.id}:$cycleStartEpochDay:$level"
            // Skip if this level (or, for 80%, the higher one) was already announced.
            if (key in alreadySent || (level == 80 && "${line.category.id}:$cycleStartEpochDay:100" in alreadySent)) return@mapNotNull null
            val name = line.category.name
            if (level == 100) {
                BudgetAlert(key, "$name budget exceeded", "You're ${Money.formatWhole(used - line.limit)} over your ${Money.formatWhole(line.limit)} $name budget this cycle.")
            } else {
                BudgetAlert(key, "$name budget at ${line.usedBp / 100}%", "${Money.formatWhole(line.remaining.coerceAtLeast(0))} left of ${Money.formatWhole(line.limit)} for $name until payday.")
            }
        }

    /** Drops keys from earlier cycles so the stored set never grows without bound. */
    fun prune(sent: Set<String>, cycleStartEpochDay: Long): Set<String> =
        sent.filter { it.split(':').getOrNull(1)?.toLongOrNull() == cycleStartEpochDay }.toSet()
}
