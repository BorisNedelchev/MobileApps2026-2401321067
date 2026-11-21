package com.bagofholding.data.repository

import com.bagofholding.data.dao.CatalogDao
import com.bagofholding.data.dao.CharacterDao
import com.bagofholding.data.dao.InventoryDao
import com.bagofholding.data.model.CatalogItem
import com.bagofholding.data.model.CharacterWithStats
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.data.model.toInventoryItem
import com.bagofholding.domain.InventoryMerger
import com.bagofholding.domain.InventoryRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Plain SQLite has no reactive queries (unlike Room), so every write bumps this
 * counter and every observed query re-runs when it changes.
 */
class DataChangeNotifier {
    private val version = MutableStateFlow(0L)
    val changes: StateFlow<Long> = version.asStateFlow()

    fun notifyChanged() {
        version.update { it + 1 }
    }
}

class CharacterRepository(
    private val dao: CharacterDao,
    private val notifier: DataChangeNotifier,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeCharacters(): Flow<List<CharacterWithStats>> =
        notifier.changes.map { dao.getAllWithStats() }.flowOn(io)

    fun observeCharacter(id: Long): Flow<PlayerCharacter?> =
        notifier.changes.map { dao.getById(id) }.flowOn(io)

    suspend fun getCharacter(id: Long): PlayerCharacter? = withContext(io) { dao.getById(id) }

    /** Inserts a new character (id == 0) or updates an existing one. Returns the id. */
    suspend fun save(character: PlayerCharacter): Long = withContext(io) {
        val id = if (character.id == 0L) {
            dao.insert(character)
        } else {
            dao.update(character)
            character.id
        }
        notifier.notifyChanged()
        id
    }

    /** Deletes the character; its inventory is removed by ON DELETE CASCADE. */
    suspend fun delete(id: Long) = withContext(io) {
        dao.delete(id)
        notifier.notifyChanged()
    }
}

/** Result of pressing "-" or "+" on an inventory row. */
sealed interface QuantityChange {
    data class Updated(val item: InventoryItem) : QuantityChange
    data class Removed(val item: InventoryItem) : QuantityChange
    data object NotFound : QuantityChange
}

class InventoryRepository(
    private val dao: InventoryDao,
    private val notifier: DataChangeNotifier,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    // Fast taps on "+" / "-" start several coroutines; read-modify-write must not interleave.
    private val quantityMutex = Mutex()

    fun observeInventory(characterId: Long): Flow<List<InventoryItem>> =
        notifier.changes.map { dao.getForCharacter(characterId) }.flowOn(io)

    suspend fun getInventory(characterId: Long): List<InventoryItem> =
        withContext(io) { dao.getForCharacter(characterId) }

    /** Inserts a new item (id == 0) or updates an existing one. */
    suspend fun save(item: InventoryItem): Long = withContext(io) {
        val sanitized = item.copy(
            name = item.name.trim(),
            quantity = InventoryRules.clampQuantity(item.quantity),
            weight = item.weight.coerceAtLeast(0.0),
            costCp = item.costCp.coerceAtLeast(0),
        )
        val id = if (sanitized.id == 0L) {
            dao.insert(sanitized)
        } else {
            dao.update(sanitized)
            sanitized.id
        }
        notifier.notifyChanged()
        id
    }

    /**
     * Adds a catalog item to the character. If the character already owns that catalog
     * item the existing stack grows instead of creating a duplicate row.
     */
    suspend fun addFromCatalog(characterId: Long, catalogItem: CatalogItem, quantity: Int = 1): InventoryItem =
        quantityMutex.withLock {
            withContext(io) { addFromCatalogLocked(characterId, catalogItem, quantity) }
        }

    private fun addFromCatalogLocked(characterId: Long, catalogItem: CatalogItem, quantity: Int): InventoryItem {
        val existing = if (catalogItem.id > 0) dao.findByCatalogId(characterId, catalogItem.id) else null
        val result = if (existing != null) {
            val updated = existing.copy(quantity = InventoryRules.applyDelta(existing.quantity, quantity))
            dao.update(updated)
            updated
        } else {
            val newItem = catalogItem.toInventoryItem(characterId, InventoryRules.clampQuantity(quantity))
            newItem.copy(id = dao.insert(newItem))
        }
        notifier.notifyChanged()
        return result
    }

    /** Changes the quantity by [delta]. Reaching zero removes the row (the caller can offer undo). */
    suspend fun changeQuantity(itemId: Long, delta: Int): QuantityChange = quantityMutex.withLock {
        withContext(io) { changeQuantityLocked(itemId, delta) }
    }

    private fun changeQuantityLocked(itemId: Long, delta: Int): QuantityChange {
        val item = dao.getById(itemId) ?: return QuantityChange.NotFound
        val newQuantity = InventoryRules.applyDelta(item.quantity, delta)
        val change = if (newQuantity == 0) {
            dao.delete(item.id)
            QuantityChange.Removed(item)
        } else {
            val updated = item.copy(quantity = newQuantity)
            dao.update(updated)
            QuantityChange.Updated(updated)
        }
        notifier.notifyChanged()
        return change
    }

    suspend fun delete(itemId: Long): InventoryItem? = withContext(io) {
        val item = dao.getById(itemId)
        if (item != null) {
            dao.delete(itemId)
            notifier.notifyChanged()
        }
        item
    }

    /** Puts a removed item back (used by the "Undo" snackbar action). */
    suspend fun restore(item: InventoryItem): Long = withContext(io) {
        val id = dao.insert(item.copy(id = 0))
        notifier.notifyChanged()
        id
    }

    /**
     * Imports items received from another device. With [merge] = true, items that match an
     * existing stack (same name and category) increase its quantity; otherwise all are inserted.
     */
    suspend fun importItems(characterId: Long, items: List<InventoryItem>, merge: Boolean) = withContext(io) {
        val existing = if (merge) dao.getForCharacter(characterId) else emptyList()
        val plan = InventoryMerger.plan(characterId, existing, items)
        dao.applyBatch(plan.toInsert, plan.toUpdate)
        notifier.notifyChanged()
    }
}

class CatalogRepository(
    private val dao: CatalogDao,
    private val notifier: DataChangeNotifier,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Emits whenever any data changes, so screens can re-run their search. */
    val changes: Flow<Long> get() = notifier.changes

    suspend fun search(query: String, category: ItemCategory?): List<CatalogItem> =
        withContext(io) { dao.search(query, category) }

    suspend fun saveCustom(item: CatalogItem): Long = withContext(io) {
        val custom = item.copy(name = item.name.trim(), isCustom = true)
        val id = if (custom.id == 0L) {
            dao.insert(custom)
        } else {
            dao.update(custom)
            custom.id
        }
        notifier.notifyChanged()
        id
    }

    /** Only custom items can be deleted; built-in items are protected. Returns true if deleted. */
    suspend fun deleteCustom(id: Long): Boolean = withContext(io) {
        val item = dao.getById(id)
        if (item == null || !item.isCustom) return@withContext false
        dao.delete(id)
        notifier.notifyChanged()
        true
    }
}
