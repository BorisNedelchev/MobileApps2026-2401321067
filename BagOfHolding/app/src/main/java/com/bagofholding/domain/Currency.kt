package com.bagofholding.domain

import kotlin.math.roundToLong

/** D&D coins. Everything is stored in copper pieces: 1 gp = 10 sp = 100 cp. */
data class Coins(val gp: Long, val sp: Long, val cp: Long)

object Currency {
    const val CP_PER_SP = 10L
    const val CP_PER_GP = 100L

    fun toCopper(gp: Long, sp: Long, cp: Long): Long = gp * CP_PER_GP + sp * CP_PER_SP + cp

    fun split(totalCp: Long): Coins {
        val positive = totalCp.coerceAtLeast(0)
        return Coins(
            gp = positive / CP_PER_GP,
            sp = (positive % CP_PER_GP) / CP_PER_SP,
            cp = positive % CP_PER_SP,
        )
    }

    /** "12 gp 5 sp 3 cp"; zero parts are skipped; 0 becomes "0 gp". */
    fun format(totalCp: Long): String {
        val coins = split(totalCp)
        val parts = buildList {
            if (coins.gp > 0) add("${coins.gp} gp")
            if (coins.sp > 0) add("${coins.sp} sp")
            if (coins.cp > 0) add("${coins.cp} cp")
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
}
