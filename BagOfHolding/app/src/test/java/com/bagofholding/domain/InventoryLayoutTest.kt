package com.bagofholding.domain

import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun item(id: Long, order: Int, weight: Double = 1.0, quantity: Int = 1) = InventoryItem(
    id = id,
    characterId = 1,
    name = "item$id",
    sortOrder = order,
    weight = weight,
    quantity = quantity,
)

private fun bag(id: Long, order: Int, collapsed: Boolean = false, weightless: Boolean = false) =
    InventoryItem(
        id = id,
        characterId = 1,
        name = "bag$id",
        sortOrder = order,
        weight = 5.0,
        isContainer = true,
        capacity = 30.0,
        collapsed = collapsed,
        weightlessContents = weightless,
    )

class InventoryLayoutTest {

    // Screen order: item1, item2, [bag10] item3 item4, [bag20] item5
    private val inventory = listOf(
        item(5, order = 6),
        bag(20, order = 5),
        item(1, order = 0),
        item(4, order = 4),
        bag(10, order = 2),
        item(3, order = 3),
        item(2, order = 1),
    )

    @Test
    fun `items below a compartment are inside it`() {
        val sections = InventoryLayout.sections(inventory)
        assertEquals(3, sections.size)
        assertEquals(null, sections[0].container)
        assertEquals(listOf(1L, 2L), sections[0].items.map { it.id })
        assertEquals(10L, sections[1].container?.id)
        assertEquals(listOf(3L, 4L), sections[1].items.map { it.id })
        assertEquals(listOf(5L), InventoryLayout.contentsOf(inventory, 20).map { it.id })
    }

    @Test
    fun `the loose section exists even when the list starts with a compartment`() {
        val sections = InventoryLayout.sections(listOf(bag(1, 0), item(2, 1)))
        assertTrue(sections[0].items.isEmpty())
        assertEquals(listOf(2L), sections[1].items.map { it.id })
    }

    @Test
    fun `rows hide the contents of collapsed compartments`() {
        val collapsed = inventory.map { if (it.id == 10L) it.copy(collapsed = true) else it }
        val rows = InventoryLayout.rows(collapsed)
        assertEquals(listOf(1L, 2L, 10L, 20L, 5L), rows.map { it.id })
        assertTrue(rows[2] is InventoryRow.Container)
        assertTrue((rows[4] as InventoryRow.Item).inContainer)
        assertTrue(!(rows[0] as InventoryRow.Item).inContainer)
    }

    @Test
    fun `dragging an item under a compartment puts it inside`() {
        // item1 dropped right below bag20
        val reordered = InventoryLayout.reorder(inventory, listOf(2, 10, 3, 4, 20, 1, 5))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), reordered.map { it.sortOrder })
        assertEquals(listOf(1L, 5L), InventoryLayout.contentsOf(reordered, 20).map { it.id })
    }

    @Test
    fun `a collapsed compartment takes its hidden contents along`() {
        val collapsed = inventory.map { if (it.id == 10L) it.copy(collapsed = true) else it }
        // On screen: item1 item2 [bag10] [bag20] item5 -> bag10 dragged to the bottom
        val reordered = InventoryLayout.reorder(collapsed, listOf(1, 2, 20, 5, 10))
        assertEquals(listOf(1L, 2L, 20L, 5L, 10L, 3L, 4L), InventoryLayout.ordered(reordered).map { it.id })
        assertEquals(listOf(3L, 4L), InventoryLayout.contentsOf(reordered, 10).map { it.id })
    }

    @Test
    fun `deleting a compartment releases its contents to the top`() {
        val released = InventoryLayout.releaseContents(inventory, 10)
        assertEquals(listOf(3L, 4L), released.map { it.id })
        assertEquals(listOf(-2, -1), released.map { it.sortOrder })
    }

    @Test
    fun `new items go to the top and new compartments to the bottom`() {
        assertEquals(-1, InventoryLayout.sortOrderForNew(inventory, isContainer = false))
        assertEquals(7, InventoryLayout.sortOrderForNew(inventory, isContainer = true))
        assertEquals(0, InventoryLayout.sortOrderForNew(emptyList(), isContainer = false))
        assertEquals(0, InventoryLayout.sortOrderForNew(emptyList(), isContainer = true))
    }

    @Test
    fun `bag of holding contents do not count toward carried weight`() {
        val items = listOf(
            item(1, 0, weight = 2.0),
            bag(2, 1, weightless = true).copy(weight = 15.0),
            item(3, 2, weight = 100.0),
            bag(4, 3),
            item(5, 4, weight = 3.0, quantity = 2),
        )
        // 2 + 15 (bag of holding, contents ignored) + 5 (backpack) + 6
        assertEquals(28.0, InventoryLayout.carriedWeight(items), 0.001)
        val stats = InventoryCalculator.stats(items, PlayerCharacter(name = "x"))
        assertEquals(28.0, stats.totalWeight, 0.001)
        assertEquals(4, stats.totalItems) // compartments are not counted as items
    }
}

class ValueTintTest {

    @Test
    fun `cheap items stay plain and expensive items become fully golden`() {
        assertEquals(0f, ValueTint.goldFraction(0), 0f)
        assertEquals(0f, ValueTint.goldFraction(100), 0f) // 1 gp
        assertEquals(1f, ValueTint.goldFraction(50_000), 0.001f) // 500 gp
        assertEquals(1f, ValueTint.goldFraction(150_000), 0f) // 1500 gp, capped
    }

    @Test
    fun `gold grows with value`() {
        val ten = ValueTint.goldFraction(1_000)
        val fifty = ValueTint.goldFraction(5_000)
        assertTrue(ten > 0f && fifty > ten && fifty < 1f)
    }
}

class CompartmentMergeTest {

    @Test
    fun `received compartments keep their contents and go to the bottom`() {
        val existing = listOf(
            InventoryItem(id = 1, characterId = 7, name = "Torch", category = ItemCategory.CONSUMABLE, sortOrder = 0),
            InventoryItem(id = 2, characterId = 7, name = "Sack", isContainer = true, sortOrder = 1),
        )
        val incoming = listOf(
            InventoryItem(characterId = 0, name = "Torch", category = ItemCategory.CONSUMABLE, quantity = 2),
            InventoryItem(characterId = 0, name = "Rope"),
            InventoryItem(characterId = 0, name = "Backpack", isContainer = true, capacity = 30.0, collapsed = true),
            InventoryItem(characterId = 0, name = "Torch", category = ItemCategory.CONSUMABLE),
        )

        val plan = InventoryMerger.plan(7, existing, incoming)

        assertEquals(3, plan.toUpdate.single().quantity) // loose torches stacked
        val inserted = plan.toInsert.associateBy { it.name }
        assertEquals(-1, inserted.getValue("Rope").sortOrder) // loose: on top
        assertEquals(2, inserted.getValue("Backpack").sortOrder) // after the existing Sack
        assertEquals(3, plan.toInsert.last().sortOrder) // the torch inside the backpack follows it
        assertTrue(!inserted.getValue("Backpack").collapsed)

        val after = existing.map { e -> plan.toUpdate.firstOrNull { it.id == e.id } ?: e } + plan.toInsert
        // New rows have id 0 until inserted; the only compartment with id 0 is the received backpack.
        assertEquals(listOf("Torch"), InventoryLayout.contentsOf(after, 0).map { it.name })
        assertTrue(InventoryLayout.contentsOf(after, 2).isEmpty()) // nothing was put into the existing sack
    }
}
