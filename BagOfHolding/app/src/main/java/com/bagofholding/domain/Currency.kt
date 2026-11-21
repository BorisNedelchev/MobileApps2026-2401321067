package com.bagofholding.domain

import com.bagofholding.data.model.Coin
import com.bagofholding.data.model.Purse
import java.util.Locale
import kotlin.math.roundToLong

/** Formatting and parsing of prices. Prices are stored in copper pieces (1 gp = 10 sp = 100 cp). */
object Currency {
    const val CP_PER_SP = 10L
    const val CP_PER_GP = 100L

    /** "12 gp 5 sp 3 cp"; zero parts are skipped; 0 becomes "0 gp". */
    fun format(totalCp: Long): String {
        val positive = totalCp.coerceAtLeast(0)
        val gp = positive / CP_PER_GP
        val sp = (positive % CP_PER_GP) / CP_PER_SP
        val cp = positive % CP_PER_SP
        val parts = buildList {
            if (gp > 0) add("$gp gp")
            if (sp > 0) add("$sp sp")
            if (cp > 0) add("$cp cp")
        }
        return if (parts.isEmpty()) "0 gp" else parts.joinToString(" ")
    }

    /** Parses a gold amount typed by the user ("15", "0.5", "1,25") into copper. Null if invalid. */
    fun parseGold(text: String): Long? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty()) return 0L
        val value = normalized.toDoubleOrNull() ?: return null
        if (value < 0 || value.isNaN() || value.isInfinite()) return null
        return (value * CP_PER_GP).roundToLong()
    }

    /** Copper to a short gold string for edit fields: 1550 -> "15.5", 1500 -> "15". */
    fun toGoldInput(totalCp: Long): String {
        val gp = totalCp / CP_PER_GP
        val rest = totalCp % CP_PER_GP
        return when {
            rest == 0L -> gp.toString()
            rest % CP_PER_SP == 0L -> "$gp.${rest / CP_PER_SP}"
            else -> "$gp.${rest.toString().padStart(2, '0')}"
        }
    }

    /** Short coin label: "pp", "gp", "ep", "sp", "cp". */
    fun label(coin: Coin): String = coin.name.lowercase(Locale.ROOT)

    /** "2 pp, 12 gp, 5 sp" – only the coins the character actually has; "no coins" when empty. */
    fun formatPurse(purse: Purse): String {
        val parts = Coin.entries.filter { purse[it] > 0 }.map { "${purse[it]} ${label(it)}" }
        return if (parts.isEmpty()) "no coins" else parts.joinToString(", ")
    }
}

/** What the coin dialog does with the typed amount. */
enum class CoinOperation { ADD, REMOVE, SET }

object PurseRules {
    const val MAX_COINS = 10_000_000L

    /**
     * Applies [operation] to the [current] number of coins. Returns null when the result would be
     * negative (paying more than you have) or unrealistically large, or when [amount] is negative.
     */
    fun apply(current: Long, operation: CoinOperation, amount: Long): Long? {
        if (amount < 0) return null
        val result = when (operation) {
            CoinOperation.ADD -> current + amount
            CoinOperation.REMOVE -> current - amount
            CoinOperation.SET -> amount
        }
        return result.takeIf { it in 0L..MAX_COINS }
    }
}
