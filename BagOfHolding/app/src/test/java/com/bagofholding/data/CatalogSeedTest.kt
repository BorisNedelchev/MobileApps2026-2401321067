package com.bagofholding.data

import com.bagofholding.data.db.CatalogSeed
import com.bagofholding.data.model.ItemCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CatalogSeedTest {

    private val items = CatalogSeed.items

    @Test
    fun `built-in names are unique`() {
        val names = items.map { it.name.lowercase(Locale.ROOT) }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `built-in items have sane values`() {
        items.forEach { item ->
            assertTrue(item.name, item.name.isNotBlank())
            assertTrue(item.name, item.weight >= 0.0)
            assertTrue(item.name, item.costCp >= 0)
            assertTrue(item.name, !item.isCustom)
        }
    }

    @Test
    fun `every category except OTHER has built-in items`() {
        val used = items.map { it.category }.toSet()
        assertEquals(ItemCategory.entries.toSet() - ItemCategory.OTHER, used)
    }

    @Test
    fun `known SRD prices are correct`() {
        val byName = items.associateBy { it.name }
        assertEquals(1500L, byName.getValue("Longsword").costCp)
        assertEquals(65.0, byName.getValue("Plate armor").weight, 0.0)
        assertEquals(5000L, byName.getValue("Potion of healing").costCp)
    }
}
