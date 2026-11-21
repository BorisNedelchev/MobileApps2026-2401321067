package com.bagofholding.domain

import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import java.util.Locale

object InventoryRules {
    const val MAX_QUANTITY = 9_999

    fun clampQuantity(quantity: Int): Int = quantity.coerceIn(0, MAX_QUANTITY)

    /** Adds [delta] to [current] and keeps the result between 0 and [MAX_QUANTITY]. */
    fun applyDelta(current: Int, delta: Int): Int {
        val sum = current.toLong() + delta
        return sum.coerceIn(0L, MAX_QUANTITY.toLong()).toInt()
    }
}

/**
 * Encumbrance from the D&D 5e "variant encumbrance" rule:
 * up to 5 x STR lb is fine, up to 10 x STR is encumbered, up to 15 x STR (the carrying
 * capacity) is heavily encumbered, above that the character cannot move the load.
 */
enum class Encumbrance { UNENCUMBERED, ENCUMBERED, HEAVILY_ENCUMBERED, OVER_CAPACITY }

data class InventoryStats(
    val totalItems: Int,
    val totalWeight: Double,
    val totalValueCp: Long,
    val carryingCapacity: Double,
    val encumbrance: Encumbrance,
)

object InventoryCalculator {
    private const val LIGHT_LOAD_PER_STR = 5
    private const val HEAVY_LOAD_PER_STR = 10
    private const val CAPACITY_PER_STR = 15

    fun carryingCapacity(strength: Int): Double = (strength.coerceAtLeast(0) * CAPACITY_PER_STR).toDouble()

    fun encumbrance(totalWeight: Double, strength: Int): Encumbrance {
        val str = strength.coerceAtLeast(0)
        return when {
            totalWeight <= str * LIGHT_LOAD_PER_STR -> Encumbrance.UNENCUMBERED
            totalWeight <= str * HEAVY_LOAD_PER_STR -> Encumbrance.ENCUMBERED
            totalWeight <= str * CAPACITY_PER_STR -> Encumbrance.HEAVILY_ENCUMBERED
            else -> Encumbrance.OVER_CAPACITY
        }
    }

    fun stats(items: List<InventoryItem>, strength: Int): InventoryStats {
        val weight = items.sumOf { it.weight * it.quantity }
        return InventoryStats(
            totalItems = items.sumOf { it.quantity },
            totalWeight = weight,
            totalValueCp = items.sumOf { it.costCp * it.quantity },
            carryingCapacity = carryingCapacity(strength),
            encumbrance = encumbrance(weight, strength),
        )
    }
}

/** Formats weights like "3 lb", "0.25 lb", "12.5 lb". */
object WeightFormat {
    fun format(pounds: Double): String {
        val rounded = Math.round(pounds * 100) / 100.0
        val text = if (rounded == Math.floor(rounded)) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
        return "$text lb"
    }
}

data class MergePlan(val toInsert: List<InventoryItem>, val toUpdate: List<InventoryItem>)

/** Decides how received items are combined with a character's existing inventory. */
object InventoryMerger {

    private fun key(item: InventoryItem): MergeKey = item.name.trim().lowercase(Locale.ROOT) to item.category

    fun plan(characterId: Long, existing: List<InventoryItem>, incoming: List<InventoryItem>): MergePlan {
        val existingByKey = LinkedHashMap<MergeKey, InventoryItem>()
        existing.forEach { existingByKey.putIfAbsent(key(it), it) }
        val changedKeys = LinkedHashSet<MergeKey>()
        val inserts = LinkedHashMap<MergeKey, InventoryItem>()

        incoming.filter { it.name.isNotBlank() && it.quantity > 0 }.forEach { raw ->
            val item = raw.copy(id = 0, characterId = characterId, name = raw.name.trim(), catalogId = null)
            val key = key(item)
            val target = existingByKey[key]
            if (target != null) {
                existingByKey[key] = target.copy(quantity = InventoryRules.applyDelta(target.quantity, item.quantity))
                changedKeys += key
            } else {
                val pending = inserts[key]
                inserts[key] = pending?.copy(quantity = InventoryRules.applyDelta(pending.quantity, item.quantity))
                    ?: item.copy(quantity = InventoryRules.clampQuantity(item.quantity))
            }
        }

        return MergePlan(
            toInsert = inserts.values.toList(),
            toUpdate = changedKeys.map { existingByKey.getValue(it) },
        )
    }
}

private typealias MergeKey = Pair<String, ItemCategory>

/** Plain-text inventory used by the Android share sheet (Share Intent). */
object InventoryTextFormatter {
    fun format(character: PlayerCharacter, items: List<InventoryItem>): String = buildString {
        append(character.name)
        val details = listOf(character.race, character.characterClass).filter { it.isNotBlank() }
        if (details.isNotEmpty()) append(" - ").append(details.joinToString(" "))
        append(" (level ").append(character.level).append(")\n")
        append("Purse: ").append(Currency.format(character.purseCp)).append("\n\n")
        if (items.isEmpty()) {
            append("Inventory is empty.\n")
        } else {
            items.forEach { item ->
                append("- ").append(item.name).append(" x").append(item.quantity)
                if (item.weight > 0) append(" (").append(WeightFormat.format(item.weight * item.quantity)).append(")")
                append("\n")
            }
        }
        val stats = InventoryCalculator.stats(items, character.strength)
        append("\nTotal weight: ").append(WeightFormat.format(stats.totalWeight))
        append(" / ").append(WeightFormat.format(stats.carryingCapacity))
        append("\nTotal value: ").append(Currency.format(stats.totalValueCp))
    }
}
