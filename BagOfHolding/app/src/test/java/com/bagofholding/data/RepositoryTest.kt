package com.bagofholding.data

import com.bagofholding.data.model.CatalogItem
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.data.repository.CatalogRepository
import com.bagofholding.data.repository.CharacterRepository
import com.bagofholding.data.repository.DataChangeNotifier
import com.bagofholding.data.repository.InventoryRepository
import com.bagofholding.data.repository.QuantityChange
import com.bagofholding.testing.FakeCatalogDao
import com.bagofholding.testing.FakeCharacterDao
import com.bagofholding.testing.FakeInventoryDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val notifier = DataChangeNotifier()
    private val inventoryDao = FakeInventoryDao()
    private val characterDao = FakeCharacterDao(inventoryDao)
    private val catalogDao = FakeCatalogDao()
    private val characters = CharacterRepository(characterDao, notifier, dispatcher)
    private val inventory = InventoryRepository(inventoryDao, notifier, dispatcher)
    private val catalog = CatalogRepository(catalogDao, notifier, dispatcher)

    private val longsword = CatalogItem(id = 5, name = "Longsword", category = ItemCategory.WEAPON, weight = 3.0)

    @Test
    fun `save inserts then updates a character`() = runTest(dispatcher) {
        val id = characters.save(PlayerCharacter(name = "Ana"))
        characters.save(PlayerCharacter(id = id, name = "Ana the Bold"))
        assertEquals("Ana the Bold", characters.getCharacter(id)?.name)
        assertEquals(1, characters.observeCharacters().first().size)
    }

    @Test
    fun `deleting a character removes its items`() = runTest(dispatcher) {
        val id = characters.save(PlayerCharacter(name = "Ana"))
        inventory.addFromCatalog(id, longsword)
        characters.delete(id)
        assertNull(characters.getCharacter(id))
        assertTrue(inventory.getInventory(id).isEmpty())
    }

    @Test
    fun `adding the same catalog item twice stacks it`() = runTest(dispatcher) {
        inventory.addFromCatalog(1, longsword)
        val second = inventory.addFromCatalog(1, longsword, quantity = 2)
        assertEquals(3, second.quantity)
        assertEquals(1, inventory.getInventory(1).size)
    }

    @Test
    fun `decrementing to zero removes the item and restore brings it back`() = runTest(dispatcher) {
        val item = inventory.addFromCatalog(1, longsword)
        val change = inventory.changeQuantity(item.id, -1)
        assertTrue(change is QuantityChange.Removed)
        assertTrue(inventory.getInventory(1).isEmpty())

        inventory.restore((change as QuantityChange.Removed).item)
        assertEquals("Longsword", inventory.getInventory(1).single().name)
    }

    @Test
    fun `incrementing updates quantity and unknown ids are reported`() = runTest(dispatcher) {
        val item = inventory.addFromCatalog(1, longsword)
        val change = inventory.changeQuantity(item.id, +4)
        assertEquals(5, (change as QuantityChange.Updated).item.quantity)
        assertEquals(QuantityChange.NotFound, inventory.changeQuantity(999, 1))
    }

    @Test
    fun `save sanitizes custom items`() = runTest(dispatcher) {
        val id = inventory.save(InventoryItem(characterId = 1, name = "  Gem ", quantity = 20_000, weight = -1.0))
        val saved = inventory.getInventory(1).single { it.id == id }
        assertEquals("Gem", saved.name)
        assertEquals(9_999, saved.quantity)
        assertEquals(0.0, saved.weight, 0.0)
    }

    @Test
    fun `import with merge stacks matching items`() = runTest(dispatcher) {
        inventory.save(InventoryItem(characterId = 1, name = "Torch", category = ItemCategory.CONSUMABLE, quantity = 2))
        val received = listOf(
            InventoryItem(characterId = 0, name = "torch", category = ItemCategory.CONSUMABLE, quantity = 3),
            InventoryItem(characterId = 0, name = "Lute", category = ItemCategory.TOOL),
        )
        inventory.importItems(1, received, merge = true)
        val items = inventory.getInventory(1).associateBy { it.name }
        assertEquals(5, items.getValue("Torch").quantity)
        assertEquals(1, items.getValue("Lute").quantity)
    }

    @Test
    fun `observers see every change`() = runTest(dispatcher) {
        val before = inventory.observeInventory(1).first()
        inventory.addFromCatalog(1, longsword)
        val after = inventory.observeInventory(1).first()
        assertTrue(before.isEmpty())
        assertEquals(1, after.size)
    }

    @Test
    fun `only custom catalog items can be deleted`() = runTest(dispatcher) {
        val builtInId = catalogDao.insert(longsword.copy(id = 0, isCustom = false))
        val customId = catalog.saveCustom(CatalogItem(name = " Homebrew Gem "))
        assertFalse(catalog.deleteCustom(builtInId))
        assertTrue(catalog.deleteCustom(customId))
        assertEquals(listOf("Longsword"), catalog.search("", null).map { it.name })
        assertTrue(catalog.search("gem", null).isEmpty())
    }
}
