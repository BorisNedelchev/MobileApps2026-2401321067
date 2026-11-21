package com.bagofholding.testing

import com.bagofholding.data.dao.CatalogDao
import com.bagofholding.data.dao.CharacterDao
import com.bagofholding.data.dao.InventoryDao
import com.bagofholding.data.model.CatalogItem
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.network.DiscoveredHost
import com.bagofholding.network.ServiceDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** In-memory replacements for the SQLite DAOs. */
class FakeInventoryDao : InventoryDao {
    val rows = linkedMapOf<Long, InventoryItem>()
    private var nextId = 1L

    override fun getForCharacter(characterId: Long) = rows.values
        .filter { it.characterId == characterId }
        .sortedWith(compareBy<InventoryItem>({ it.sortOrder }, { it.id }))

    override fun getById(id: Long) = rows[id]

    override fun findByCatalogId(characterId: Long, catalogId: Long) =
        rows.values.firstOrNull { it.characterId == characterId && it.catalogId == catalogId }

    override fun insert(item: InventoryItem): Long {
        val id = nextId++
        rows[id] = item.copy(id = id)
        return id
    }

    override fun update(item: InventoryItem): Int {
        if (item.id !in rows) return 0
        rows[item.id] = item
        return 1
    }

    override fun delete(id: Long): Int = if (rows.remove(id) != null) 1 else 0

    override fun applyBatch(toInsert: List<InventoryItem>, toUpdate: List<InventoryItem>) {
        toInsert.forEach { insert(it) }
        toUpdate.forEach { update(it) }
    }
}

class FakeCharacterDao(private val inventory: FakeInventoryDao) : CharacterDao {
    val rows = linkedMapOf<Long, PlayerCharacter>()
    private var nextId = 1L

    override fun getAll(): List<PlayerCharacter> = rows.values.toList().asReversed()

    override fun getById(id: Long) = rows[id]

    override fun insert(character: PlayerCharacter): Long {
        val id = nextId++
        rows[id] = character.copy(id = id)
        return id
    }

    override fun update(character: PlayerCharacter): Int {
        if (character.id !in rows) return 0
        rows[character.id] = character
        return 1
    }

    override fun delete(id: Long): Int {
        inventory.rows.values.removeAll { it.characterId == id } // ON DELETE CASCADE
        return if (rows.remove(id) != null) 1 else 0
    }
}

class FakeCatalogDao : CatalogDao {
    val rows = linkedMapOf<Long, CatalogItem>()
    private var nextId = 1L

    override fun search(query: String, category: ItemCategory?) = rows.values
        .filter { it.name.contains(query.trim(), ignoreCase = true) }
        .filter { category == null || it.category == category }

    override fun getById(id: Long) = rows[id]

    override fun insert(item: CatalogItem): Long {
        val id = nextId++
        rows[id] = item.copy(id = id)
        return id
    }

    override fun update(item: CatalogItem): Int {
        if (item.id !in rows) return 0
        rows[item.id] = item
        return 1
    }

    override fun delete(id: Long): Int = if (rows.remove(id) != null) 1 else 0
}

class FakeServiceDiscovery : ServiceDiscovery {
    val discovered = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    override val hosts: StateFlow<List<DiscoveredHost>> = discovered
    var registeredName: String? = null
    var discovering = false

    override fun register(displayName: String, port: Int) {
        registeredName = displayName
    }

    override fun unregister() {
        registeredName = null
    }

    override fun startDiscovery() {
        discovering = true
    }

    override fun stopDiscovery() {
        discovering = false
    }
}

/** Replaces Dispatchers.Main (used by viewModelScope) with a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
