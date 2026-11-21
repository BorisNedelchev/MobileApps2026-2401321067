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
 * Powerful Build counts the character as one size larger, which doubles every limit.
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
    private const val POWERFUL_BUILD_MULTIPLIER = 2

    /** Pounds of carrying capacity for each point of Strength. */
    private fun perStrength(base: Int, powerfulBuild: Boolean): Int =
        if (powerfulBuild) base * POWERFUL_BUILD_MULTIPLIER else base

    fun carryingCapacity(strength: Int, powerfulBuild: Boolean = false): Double =
        (strength.coerceAtLeast(0) * perStrength(CAPACITY_PER_STR, powerfulBuild)).toDouble()

    fun encumbrance(totalWeight: Double, strength: Int, powerfulBuild: Boolean = false): Encumbrance {
        val str = strength.coerceAtLeast(0)
        return when {
            totalWeight <= str * perStrength(LIGHT_LOAD_PER_STR, powerfulBuild) -> Encumbrance.UNENCUMBERED
            totalWeight <= str * perStrength(HEAVY_LOAD_PER_STR, powerfulBuild) -> Encumbrance.ENCUMBERED
            totalWeight <= str * perStrength(CAPACITY_PER_STR, powerfulBuild) -> Encumbrance.HEAVILY_ENCUMBERED
            else -> Encumbrance.OVER_CAPACITY
        }
    }

    /** Totals for one character. Compartments are not counted as items; see [InventoryLayout.carriedWeight]. */
    fun stats(items: List<InventoryItem>, character: PlayerCharacter): InventoryStats {
        val weight = InventoryLayout.carriedWeight(items)
        return InventoryStats(
            totalItems = items.filterNot { it.isContainer }.sumOf { it.quantity },
            totalWeight = weight,
            totalValueCp = items.sumOf { it.costCp * it.quantity },
            carryingCapacity = carryingCapacity(character.strength, character.powerfulBuild),
            encumbrance = encumbrance(weight, character.strength, character.powerfulBuild),
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

    /**
     * Loose received items stack onto matching existing items (same name and category) or are
     * added at the top. Received storage compartments are added at the bottom together with
     * their contents, so the received structure is kept. The order of [incoming] is the
     * received screen order.
     */
    fun plan(characterId: Long, existing: List<InventoryItem>, incoming: List<InventoryItem>): MergePlan {
        val existingByKey = LinkedHashMap<MergeKey, InventoryItem>()
        existing.filterNot { it.isContainer }.forEach { existingByKey.putIfAbsent(key(it), it) }
        val changedKeys = LinkedHashSet<MergeKey>()
        val looseInserts = LinkedHashMap<MergeKey, InventoryItem>()
        val bottomInserts = mutableListOf<InventoryItem>()

        val cleaned = incoming
            .filter { it.name.isNotBlank() && it.quantity > 0 }
            .mapIndexed { index, raw ->
                raw.copy(
                    id = 0,
                    characterId = characterId,
                    name = raw.name.trim(),
                    catalogId = null,
                    sortOrder = index,
                    quantity = InventoryRules.clampQuantity(raw.quantity),
                )
            }
        val sections = InventoryLayout.sections(cleaned)

        sections.first().items.forEach { item ->
            val key = key(item)
            val target = existingByKey[key]
            if (target != null) {
                existingByKey[key] = target.copy(quantity = InventoryRules.applyDelta(target.quantity, item.quantity))
                changedKeys += key
            } else {
                val pending = looseInserts[key]
                looseInserts[key] = pending?.copy(quantity = InventoryRules.applyDelta(pending.quantity, item.quantity))
                    ?: item
            }
        }
        sections.drop(1).forEach { section ->
            bottomInserts += checkNotNull(section.container).copy(collapsed = false)
            bottomInserts += section.items
        }

        val top = existing.minOfOrNull { it.sortOrder } ?: 0
        val bottom = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val loose = looseInserts.values.mapIndexed { i, item -> item.copy(sortOrder = top - looseInserts.size + i) }
        val atBottom = bottomInserts.mapIndexed { i, item -> item.copy(sortOrder = bottom + i) }
        return MergePlan(
            toInsert = loose + atBottom,
            toUpdate = changedKeys.map { existingByKey.getValue(it) },
        )
    }
}

private typealias MergeKey = Pair<String, ItemCategory>

/** Plain-text inventory used by the Android share sheet (Share Intent). */
object InventoryTextFormatter {
    fun format(character: PlayerCharacter, items: List<InventoryItem>): String = buildString {
        append(character.name).append(" (STR ").append(character.strength)
        if (character.powerfulBuild) append(", powerful build")
        append(")\n")
        append("Purse: ").append(Currency.formatPurse(character.purse))
        append(" (worth ").append(Currency.format(character.purse.totalCp)).append(")\n\n")
        if (items.isEmpty()) append("Inventory is empty.\n")
        InventoryLayout.sections(items).forEach { section ->
            val container = section.container
            val indent = if (container == null) "" else "    "
            if (container != null) {
                append("[").append(container.name).append("] ")
                append(WeightFormat.format(section.contentsWeight))
                if (container.capacity > 0) append(" / ").append(WeightFormat.format(container.capacity))
                if (container.weightlessContents) append(" (contents weigh nothing)")
                append("\n")
            }
            section.items.forEach { item ->
                append(indent).append("- ").append(item.name).append(" x").append(item.quantity)
                if (item.weight > 0) append(" (").append(WeightFormat.format(item.weight * item.quantity)).append(")")
                append("\n")
            }
        }
        val stats = InventoryCalculator.stats(items, character)
        append("\nTotal weight: ").append(WeightFormat.format(stats.totalWeight))
        append(" / ").append(WeightFormat.format(stats.carryingCapacity))
        append("\nItems value: ").append(Currency.format(stats.totalValueCp))
    }
}
