package com.bagofholding.domain

import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRulesTest {

    @Test
    fun `applyDelta never goes below zero or above the maximum`() {
        assertEquals(4, InventoryRules.applyDelta(3, 1))
        assertEquals(0, InventoryRules.applyDelta(1, -5))
        assertEquals(InventoryRules.MAX_QUANTITY, InventoryRules.applyDelta(InventoryRules.MAX_QUANTITY, 1))
        assertEquals(InventoryRules.MAX_QUANTITY, InventoryRules.applyDelta(5, Int.MAX_VALUE))
    }
}

class InventoryCalculatorTest {

    private fun item(weight: Double, quantity: Int, cost: Long = 0) =
        InventoryItem(characterId = 1, name = "x", weight = weight, quantity = quantity, costCp = cost)

    @Test
    fun `stats sum weight and value per unit times quantity`() {
        val stats = InventoryCalculator.stats(listOf(item(3.0, 2, 1500), item(0.5, 4, 10)), strength = 10)
        assertEquals(8.0, stats.totalWeight, 0.001)
        assertEquals(3040L, stats.totalValueCp)
        assertEquals(6, stats.totalItems)
        assertEquals(150.0, stats.carryingCapacity, 0.001)
    }

    @Test
    fun `encumbrance follows the variant rule thresholds`() {
        assertEquals(Encumbrance.UNENCUMBERED, InventoryCalculator.encumbrance(50.0, 10))
        assertEquals(Encumbrance.ENCUMBERED, InventoryCalculator.encumbrance(51.0, 10))
        assertEquals(Encumbrance.HEAVILY_ENCUMBERED, InventoryCalculator.encumbrance(101.0, 10))
        assertEquals(Encumbrance.OVER_CAPACITY, InventoryCalculator.encumbrance(151.0, 10))
    }

    @Test
    fun `weight format drops useless decimals`() {
        assertEquals("3 lb", WeightFormat.format(3.0))
        assertEquals("0.25 lb", WeightFormat.format(0.25))
        assertEquals("12.5 lb", WeightFormat.format(12.5))
    }
}

class InventoryMergerTest {

    private fun item(name: String, quantity: Int, id: Long = 0, category: ItemCategory = ItemCategory.GEAR) =
        InventoryItem(id = id, characterId = 7, name = name, category = category, quantity = quantity)

    @Test
    fun `matching items increase quantity, new items are inserted`() {
        val existing = listOf(item("Torch", 2, id = 1), item("Rope", 1, id = 2))
        val incoming = listOf(item(" torch ", 3), item("Dagger", 1))

        val plan = InventoryMerger.plan(characterId = 7, existing = existing, incoming = incoming)

        assertEquals(listOf(item("Torch", 5, id = 1)), plan.toUpdate)
        assertEquals(1, plan.toInsert.size)
        assertEquals("Dagger", plan.toInsert[0].name)
        assertEquals(0L, plan.toInsert[0].id)
    }

    @Test
    fun `same name in another category is a different item`() {
        val existing = listOf(item("Bag of Holding", 1, id = 1, category = ItemCategory.MAGIC))
        val plan = InventoryMerger.plan(7, existing, listOf(item("Bag of Holding", 1, category = ItemCategory.GEAR)))
        assertTrue(plan.toUpdate.isEmpty())
        assertEquals(1, plan.toInsert.size)
    }

    @Test
    fun `duplicates in the incoming list are combined and blanks dropped`() {
        val plan = InventoryMerger.plan(9, emptyList(), listOf(item("Arrow", 10), item("arrow", 5), item(" ", 1)))
        assertEquals(1, plan.toInsert.size)
        assertEquals(15, plan.toInsert[0].quantity)
        assertEquals(9L, plan.toInsert[0].characterId)
    }
}

class InventoryTextFormatterTest {

    @Test
    fun `text contains character, items and totals`() {
        val character = PlayerCharacter(name = "Thorin", characterClass = "Fighter", race = "Dwarf", level = 5)
        val items = listOf(InventoryItem(characterId = 1, name = "Longsword", weight = 3.0, quantity = 1))
        val text = InventoryTextFormatter.format(character, items)
        assertTrue(text.startsWith("Thorin - Dwarf Fighter (level 5)"))
        assertTrue(text.contains("- Longsword x1 (3 lb)"))
        assertTrue(text.contains("Total weight: 3 lb / 150 lb"))
    }
}
