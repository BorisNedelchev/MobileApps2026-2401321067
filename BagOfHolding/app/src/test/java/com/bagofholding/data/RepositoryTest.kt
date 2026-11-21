package com.bagofholding.data

import com.bagofholding.data.model.CatalogItem
import com.bagofholding.data.model.Coin
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.data.model.Purse
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
    private val characters = CharacterRepository(characterDao, inventoryDao, notifier, dispatcher)
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
    fun `setCoins changes one coin type only`() = runTest(dispatcher) {
        val id = characters.save(PlayerCharacter(name = "Ana", purse = Purse(gp = 5, cp = 7)))
        characters.setCoins(id, Coin.EP, 4)
        characters.setCoins(id, Coin.GP, -3) // never negative
        assertEquals(Purse(gp = 0, ep = 4, cp = 7), characters.getCharacter(id)?.purse)
    }

    @Test
    fun `catalog description is copied to the inventory item`() = runTest(dispatcher) {
        val item = inventory.addFromCatalog(1, longsword.copy(description = "1d8 slashing"))
        assertEquals("1d8 slashing", item.description)
        assertEquals("1d8 slashing", inventory.observeItem(item.id).first()?.description)
    }

    @Test
    fun `new items appear on top and new compartments at the bottom`() = runTest(dispatcher) {
        inventory.save(InventoryItem(characterId = 1, name = "Rope"))
        inventory.save(InventoryItem(characterId = 1, name = "Belt", isContainer = true, quantity = 5))
        inventory.save(InventoryItem(characterId = 1, name = "Torch"))
        val names = inventory.getInventory(1).map { it.name }
        assertEquals(listOf("Torch", "Rope", "Belt"), names)
        assertEquals(1, inventory.getInventory(1).last().quantity) // compartments are always 1
    }

    @Test
    fun `drag and drop order is saved`() = runTest(dispatcher) {
        val rope = inventory.save(InventoryItem(characterId = 1, name = "Rope"))
        val belt = inventory.save(InventoryItem(characterId = 1, name = "Belt", isContainer = true))
        val torch = inventory.save(InventoryItem(characterId = 1, name = "Torch"))
        inventory.reorder(1, listOf(belt, torch, rope))
        assertEquals(listOf("Belt", "Torch", "Rope"), inventory.getInventory(1).map { it.name })
        val details = inventory.observeDetails(belt).first()
        assertEquals(listOf("Torch", "Rope"), details?.contents?.map { it.name })
    }

    @Test
    fun `collapsing and deleting a compartment`() = runTest(dispatcher) {
        val rope = inventory.save(InventoryItem(characterId = 1, name = "Rope"))
        val belt = inventory.save(InventoryItem(characterId = 1, name = "Belt", isContainer = true))
        inventory.reorder(1, listOf(belt, rope)) // rope goes into the belt
        inventory.setCollapsed(belt, true)
        assertTrue(inventory.getInventory(1).first { it.id == belt }.collapsed)

        inventory.delete(belt)
        val left = inventory.getInventory(1)
        assertEquals(listOf("Rope"), left.map { it.name })
        assertTrue(left.single().sortOrder < 0) // moved out to the top
    }

    @Test
    fun `catalog bags become compartments and are never stacked`() = runTest(dispatcher) {
        val backpack = CatalogItem(id = 9, name = "Backpack", weight = 5.0, capacity = 30.0)
        val first = inventory.addFromCatalog(1, backpack)
        val second = inventory.addFromCatalog(1, backpack)
        assertTrue(first.isContainer)
        assertEquals(30.0, first.capacity, 0.0)
        assertTrue(first.id != second.id)
        assertEquals(1, second.quantity)
        // a compartment has no +/- quantity
        assertEquals(QuantityChange.Updated(first), inventory.changeQuantity(first.id, -1))
    }

    @Test
    fun `character list weight ignores bag of holding contents`() = runTest(dispatcher) {
        val id = characters.save(PlayerCharacter(name = "Ana"))
        val bag = CatalogItem(
            id = 3,
            name = "Bag of Holding",
            weight = 15.0,
            capacity = 500.0,
            weightlessContents = true,
        )
        inventory.addFromCatalog(id, bag)
        val anvil = inventory.save(InventoryItem(characterId = id, name = "Anvil", weight = 100.0))
        val bagId = inventory.getInventory(id).first { it.isContainer }.id
        inventory.reorder(id, listOf(bagId, anvil)) // anvil into the bag
        val stats = characters.observeCharacters().first().single()
        assertEquals(15.0, stats.totalWeight, 0.001)
        assertEquals(1, stats.itemCount)
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
