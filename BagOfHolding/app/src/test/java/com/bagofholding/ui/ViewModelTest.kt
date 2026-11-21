package com.bagofholding.ui

import com.bagofholding.data.model.CatalogItem
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.data.repository.CatalogRepository
import com.bagofholding.data.repository.CharacterRepository
import com.bagofholding.data.repository.DataChangeNotifier
import com.bagofholding.data.repository.InventoryRepository
import com.bagofholding.domain.Encumbrance
import com.bagofholding.network.DiscoveredHost
import com.bagofholding.network.HostAddress
import com.bagofholding.network.ShareCodec
import com.bagofholding.network.ShareFetcher
import com.bagofholding.network.ShareFormatException
import com.bagofholding.testing.FakeCatalogDao
import com.bagofholding.testing.FakeCharacterDao
import com.bagofholding.testing.FakeInventoryDao
import com.bagofholding.testing.FakeServiceDiscovery
import com.bagofholding.testing.MainDispatcherRule
import com.bagofholding.ui.catalog.CatalogEvent
import com.bagofholding.ui.catalog.CatalogViewModel
import com.bagofholding.ui.characters.CharacterListViewModel
import com.bagofholding.ui.inventory.InventoryEvent
import com.bagofholding.ui.inventory.InventoryViewModel
import com.bagofholding.ui.share.ShareError
import com.bagofholding.ui.share.ShareEvent
import com.bagofholding.ui.share.ShareViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Shared setup: real repositories on top of in-memory fake DAOs. */
@Suppress("UnnecessaryAbstractClass")
@OptIn(ExperimentalCoroutinesApi::class)
abstract class RepositoryBackedTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    protected val dispatcher = mainRule.dispatcher
    private val notifier = DataChangeNotifier()
    protected val inventoryDao = FakeInventoryDao()
    protected val characterDao = FakeCharacterDao(inventoryDao)
    protected val catalogDao = FakeCatalogDao()
    protected val characters = CharacterRepository(characterDao, notifier, dispatcher)
    protected val inventory = InventoryRepository(inventoryDao, notifier, dispatcher)
    protected val catalog = CatalogRepository(catalogDao, notifier, dispatcher)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterListViewModelTest : RepositoryBackedTest() {

    @Test
    fun `characters are listed after save and gone after delete`() = runTest(dispatcher) {
        val viewModel = CharacterListViewModel(characters)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.characters.collect {} }

        viewModel.save(PlayerCharacter(name = "Thorin"))
        val list = viewModel.characters.value
        assertEquals(listOf("Thorin"), list?.map { it.character.name })

        viewModel.delete(list!!.single().character.id)
        assertEquals(emptyList<Any>(), viewModel.characters.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest : RepositoryBackedTest() {

    @Test
    fun `state combines character, items and encumbrance`() = runTest(dispatcher) {
        val id = characters.save(PlayerCharacter(name = "Thorin", strength = 2))
        val viewModel = InventoryViewModel(id, characters, inventory, catalog)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.saveItem(InventoryItem(characterId = 0, name = "Anvil", weight = 25.0), alsoSaveToCatalog = true)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Thorin", state.character?.name)
        assertEquals(id, state.items.single().characterId)
        assertEquals(Encumbrance.HEAVILY_ENCUMBERED, state.stats?.encumbrance)
        assertEquals("Anvil", catalogDao.rows.values.single().name)
        assertTrue(viewModel.shareText()!!.contains("Anvil"))
    }

    @Test
    fun `decrementing the last item emits ItemRemoved for undo`() = runTest(dispatcher) {
        val id = characters.save(PlayerCharacter(name = "Thorin"))
        val viewModel = InventoryViewModel(id, characters, inventory, catalog)
        inventory.save(InventoryItem(characterId = id, name = "Torch"))
        val torch = inventory.getInventory(id).single()

        viewModel.changeQuantity(torch, -1)
        val event = viewModel.events.first() as InventoryEvent.ItemRemoved
        assertEquals("Torch", event.item.name)
        assertTrue(inventory.getInventory(id).isEmpty())

        viewModel.restore(event.item)
        assertEquals(1, inventory.getInventory(id).size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest : RepositoryBackedTest() {

    @Test
    fun `search filters by text and category`() = runTest(dispatcher) {
        catalogDao.insert(CatalogItem(name = "Longsword", category = ItemCategory.WEAPON))
        catalogDao.insert(CatalogItem(name = "Rope", category = ItemCategory.GEAR))
        val viewModel = CatalogViewModel(1, catalog, inventory)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.items.collect {} }

        assertEquals(2, viewModel.items.value?.size)
        viewModel.setQuery("long")
        assertEquals(listOf("Longsword"), viewModel.items.value?.map { it.name })
        viewModel.setQuery("")
        viewModel.setCategory(ItemCategory.GEAR)
        assertEquals(listOf("Rope"), viewModel.items.value?.map { it.name })
    }

    @Test
    fun `adding an item reports the new stack size`() = runTest(dispatcher) {
        val id = catalogDao.insert(CatalogItem(name = "Arrows (20)"))
        val viewModel = CatalogViewModel(3, catalog, inventory)
        viewModel.addToInventory(catalogDao.rows.getValue(id))
        viewModel.addToInventory(catalogDao.rows.getValue(id))
        val first = viewModel.events.first() as CatalogEvent.AddedToInventory
        val second = viewModel.events.first() as CatalogEvent.AddedToInventory
        assertEquals(1, first.item.quantity)
        assertEquals(2, second.item.quantity)
    }

    @Test
    fun `without a character nothing is added`() = runTest(dispatcher) {
        val viewModel = CatalogViewModel(0, catalog, inventory)
        assertFalse(viewModel.canAddToInventory)
        viewModel.addToInventory(CatalogItem(id = 1, name = "x"))
        assertTrue(inventoryDao.rows.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest : RepositoryBackedTest() {

    private val discovery = FakeServiceDiscovery()
    private val remotePayload = ShareCodec.fromDomain(
        PlayerCharacter(name = "Mira", characterClass = "Bard"),
        listOf(
            InventoryItem(characterId = 9, name = "Lute", category = ItemCategory.TOOL),
            InventoryItem(characterId = 9, name = "Torch", category = ItemCategory.CONSUMABLE, quantity = 3),
        ),
    )

    private fun viewModel(characterId: Long, fetcher: ShareFetcher = ShareFetcher { remotePayload }) =
        ShareViewModel(characterId, characters, inventory, discovery, fetcher, localAddresses = { listOf("10.0.0.2") })

    @Test
    fun `discovery starts with the screen and hosts are exposed`() = runTest(dispatcher) {
        val viewModel = viewModel(characterId = 0)
        assertTrue(discovery.discovering)
        assertFalse(viewModel.uiState.value.canHost)
        val host = DiscoveredHost("BoH-Mira", "Mira", HostAddress("10.0.0.9", 47321))
        discovery.discovered.value = listOf(host)
        assertEquals(listOf(host), viewModel.uiState.value.discoveredHosts)
    }

    @Test
    fun `fetched inventory can be imported as a new character`() = runTest(dispatcher) {
        val viewModel = viewModel(characterId = 0)
        viewModel.fetchFromText("10.0.0.9:47321")
        assertNotNull(viewModel.uiState.value.received)

        viewModel.importAsNewCharacter()
        val event = viewModel.events.first() as ShareEvent.Imported
        assertNull(viewModel.uiState.value.received)
        assertEquals("Mira", characters.getCharacter(event.characterId)?.name)
        assertEquals(setOf("Lute", "Torch"), inventory.getInventory(event.characterId).map { it.name }.toSet())
    }

    @Test
    fun `fetched inventory can be merged into the current character`() = runTest(dispatcher) {
        val id = characters.save(PlayerCharacter(name = "Thorin"))
        inventory.save(InventoryItem(characterId = id, name = "Torch", category = ItemCategory.CONSUMABLE))
        val viewModel = viewModel(characterId = id)
        assertTrue(viewModel.uiState.value.canHost)
        assertEquals("Thorin", viewModel.uiState.value.characterName)

        viewModel.fetchFromText("10.0.0.9")
        viewModel.mergeIntoCurrentCharacter()
        val event = viewModel.events.first() as ShareEvent.Imported
        assertTrue(event.merged)
        val items = inventory.getInventory(id).associateBy { it.name }
        assertEquals(4, items.getValue("Torch").quantity)
        assertEquals(1, items.getValue("Lute").quantity)
    }

    @Test
    fun `errors are reported as events`() = runTest(dispatcher) {
        val offline = viewModel(0, ShareFetcher { throw IOException("offline") })
        offline.fetchFromText("10.0.0.9")
        assertEquals(ShareEvent.Error(ShareError.CONNECTION_FAILED), offline.events.first())
        assertFalse(offline.uiState.value.isFetching)

        val broken = viewModel(0, ShareFetcher { throw ShareFormatException("bad") })
        broken.fetchFromText("10.0.0.9")
        assertEquals(ShareEvent.Error(ShareError.BAD_DATA), broken.events.first())

        broken.fetchFromText("not an address:xyz")
        assertEquals(ShareEvent.Error(ShareError.INVALID_ADDRESS), broken.events.first())
    }

    @Test
    fun `dismissing a received inventory imports nothing`() = runTest(dispatcher) {
        val viewModel = viewModel(characterId = 0)
        viewModel.fetchFromText("10.0.0.9")
        viewModel.dismissReceived()
        assertNull(viewModel.uiState.value.received)
        assertTrue(characterDao.rows.isEmpty())
    }
}
