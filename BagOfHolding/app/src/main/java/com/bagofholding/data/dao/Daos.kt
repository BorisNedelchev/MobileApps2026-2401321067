package com.bagofholding.data.dao

import com.bagofholding.data.model.CatalogItem
import com.bagofholding.data.model.CharacterWithStats
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter

/*
 * Data access interfaces. The production implementations use plain SQLite
 * (see com.bagofholding.data.db); unit tests use in-memory fakes.
 * All methods are blocking and must be called from a background dispatcher.
 */

interface CharacterDao {
    fun getAllWithStats(): List<CharacterWithStats>
    fun getById(id: Long): PlayerCharacter?
    fun insert(character: PlayerCharacter): Long
    fun update(character: PlayerCharacter): Int
    fun delete(id: Long): Int
}

interface InventoryDao {
    fun getForCharacter(characterId: Long): List<InventoryItem>
    fun getById(id: Long): InventoryItem?
    fun findByCatalogId(characterId: Long, catalogId: Long): InventoryItem?
    fun insert(item: InventoryItem): Long
    fun update(item: InventoryItem): Int
    fun delete(id: Long): Int

    /** Inserts and updates many rows atomically. */
    fun applyBatch(toInsert: List<InventoryItem>, toUpdate: List<InventoryItem>)
}

interface CatalogDao {
    fun search(query: String, category: ItemCategory?): List<CatalogItem>
    fun getById(id: Long): CatalogItem?
    fun insert(item: CatalogItem): Long
    fun update(item: CatalogItem): Int
    fun delete(id: Long): Int
}
