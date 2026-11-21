package com.bagofholding.data.db

import android.content.ContentValues
import android.database.Cursor
import com.bagofholding.data.dao.CatalogDao
import com.bagofholding.data.dao.CharacterDao
import com.bagofholding.data.dao.InventoryDao
import com.bagofholding.data.db.DbContract.COL_ID
import com.bagofholding.data.model.CatalogItem
import com.bagofholding.data.model.CharacterWithStats
import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.data.db.DbContract.Catalog as Cat
import com.bagofholding.data.db.DbContract.Characters as Ch
import com.bagofholding.data.db.DbContract.Inventory as Inv

private const val ID_WHERE = "$COL_ID = ?"
private fun idArgs(id: Long) = arrayOf(id.toString())

class SqliteCharacterDao(private val helper: DatabaseHelper) : CharacterDao {

    override fun getAllWithStats(): List<CharacterWithStats> {
        val sql = """
            SELECT c.*,
                   COALESCE(SUM(i.${Inv.QUANTITY}), 0) AS item_count,
                   COALESCE(SUM(i.${Inv.QUANTITY} * i.${Inv.WEIGHT}), 0) AS total_weight
            FROM ${Ch.TABLE} c
            LEFT JOIN ${Inv.TABLE} i ON i.${Inv.CHARACTER_ID} = c.$COL_ID
            GROUP BY c.$COL_ID
            ORDER BY c.${Ch.CREATED_AT} DESC, c.$COL_ID DESC
        """
        return helper.readableDatabase.rawQuery(sql, null).mapRows { cursor ->
            CharacterWithStats(
                character = cursor.toCharacter(),
                itemCount = cursor.int("item_count"),
                totalWeight = cursor.double("total_weight"),
            )
        }
    }

    override fun getById(id: Long): PlayerCharacter? = helper.readableDatabase
        .query(Ch.TABLE, null, ID_WHERE, idArgs(id), null, null, null)
        .mapRows { it.toCharacter() }
        .firstOrNull()

    override fun insert(character: PlayerCharacter): Long {
        val values = character.toValues().apply { put(Ch.CREATED_AT, System.currentTimeMillis()) }
        return helper.writableDatabase.insertOrThrow(Ch.TABLE, null, values)
    }

    override fun update(character: PlayerCharacter): Int =
        helper.writableDatabase.update(Ch.TABLE, character.toValues(), ID_WHERE, idArgs(character.id))

    override fun delete(id: Long): Int = helper.writableDatabase.delete(Ch.TABLE, ID_WHERE, idArgs(id))

    private fun PlayerCharacter.toValues() = ContentValues().apply {
        put(Ch.NAME, name)
        put(Ch.CLASS, characterClass)
        put(Ch.RACE, race)
        put(Ch.LEVEL, level)
        put(Ch.STRENGTH, strength)
        put(Ch.PURSE_CP, purseCp)
    }

    private fun Cursor.toCharacter() = PlayerCharacter(
        id = long(COL_ID),
        name = string(Ch.NAME),
        characterClass = string(Ch.CLASS),
        race = string(Ch.RACE),
        level = int(Ch.LEVEL),
        strength = int(Ch.STRENGTH),
        purseCp = long(Ch.PURSE_CP),
    )
}

class SqliteInventoryDao(private val helper: DatabaseHelper) : InventoryDao {

    override fun getForCharacter(characterId: Long): List<InventoryItem> = helper.readableDatabase
        .query(
            Inv.TABLE,
            null,
            "${Inv.CHARACTER_ID} = ?",
            idArgs(characterId),
            null,
            null,
            "${Inv.CATEGORY} ASC, ${Inv.NAME} COLLATE NOCASE ASC",
        )
        .mapRows { it.toItem() }

    override fun getById(id: Long): InventoryItem? = helper.readableDatabase
        .query(Inv.TABLE, null, ID_WHERE, idArgs(id), null, null, null)
        .mapRows { it.toItem() }
        .firstOrNull()

    override fun findByCatalogId(characterId: Long, catalogId: Long): InventoryItem? = helper.readableDatabase
        .query(
            Inv.TABLE,
            null,
            "${Inv.CHARACTER_ID} = ? AND ${Inv.CATALOG_ID} = ?",
            arrayOf(characterId.toString(), catalogId.toString()),
            null,
            null,
            null,
            "1",
        )
        .mapRows { it.toItem() }
        .firstOrNull()

    override fun insert(item: InventoryItem): Long =
        helper.writableDatabase.insertOrThrow(Inv.TABLE, null, item.toValues())

    override fun update(item: InventoryItem): Int =
        helper.writableDatabase.update(Inv.TABLE, item.toValues(), ID_WHERE, idArgs(item.id))

    override fun delete(id: Long): Int = helper.writableDatabase.delete(Inv.TABLE, ID_WHERE, idArgs(id))

    override fun applyBatch(toInsert: List<InventoryItem>, toUpdate: List<InventoryItem>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            toInsert.forEach { db.insertOrThrow(Inv.TABLE, null, it.toValues()) }
            toUpdate.forEach { db.update(Inv.TABLE, it.toValues(), ID_WHERE, idArgs(it.id)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun InventoryItem.toValues() = ContentValues().apply {
        put(Inv.CHARACTER_ID, characterId)
        put(Inv.NAME, name)
        put(Inv.CATEGORY, category.name)
        put(Inv.QUANTITY, quantity)
        put(Inv.WEIGHT, weight)
        put(Inv.COST_CP, costCp)
        put(Inv.NOTES, notes)
        if (catalogId != null) put(Inv.CATALOG_ID, catalogId) else putNull(Inv.CATALOG_ID)
    }

    private fun Cursor.toItem() = InventoryItem(
        id = long(COL_ID),
        characterId = long(Inv.CHARACTER_ID),
        name = string(Inv.NAME),
        category = ItemCategory.fromName(string(Inv.CATEGORY)),
        quantity = int(Inv.QUANTITY),
        weight = double(Inv.WEIGHT),
        costCp = long(Inv.COST_CP),
        notes = string(Inv.NOTES),
        catalogId = longOrNull(Inv.CATALOG_ID),
    )
}

class SqliteCatalogDao(private val helper: DatabaseHelper) : CatalogDao {

    override fun search(query: String, category: ItemCategory?): List<CatalogItem> {
        val where = StringBuilder("1 = 1")
        val args = mutableListOf<String>()
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            where.append(" AND ${Cat.NAME} LIKE ? ESCAPE '\\'")
            args.add("%${escapeLike(trimmed)}%")
        }
        if (category != null) {
            where.append(" AND ${Cat.CATEGORY} = ?")
            args.add(category.name)
        }
        return helper.readableDatabase
            .query(
                Cat.TABLE,
                null,
                where.toString(),
                args.toTypedArray(),
                null,
                null,
                "${Cat.IS_CUSTOM} DESC, ${Cat.NAME} COLLATE NOCASE ASC",
            )
            .mapRows { it.toCatalogItem() }
    }

    override fun getById(id: Long): CatalogItem? = helper.readableDatabase
        .query(Cat.TABLE, null, ID_WHERE, idArgs(id), null, null, null)
        .mapRows { it.toCatalogItem() }
        .firstOrNull()

    override fun insert(item: CatalogItem): Long =
        helper.writableDatabase.insertOrThrow(Cat.TABLE, null, item.toValues())

    override fun update(item: CatalogItem): Int =
        helper.writableDatabase.update(Cat.TABLE, item.toValues(), ID_WHERE, idArgs(item.id))

    override fun delete(id: Long): Int = helper.writableDatabase.delete(Cat.TABLE, ID_WHERE, idArgs(id))

    private fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun CatalogItem.toValues() = ContentValues().apply {
        put(Cat.NAME, name)
        put(Cat.CATEGORY, category.name)
        put(Cat.WEIGHT, weight)
        put(Cat.COST_CP, costCp)
        put(Cat.DESCRIPTION, description)
        put(Cat.IS_CUSTOM, if (isCustom) 1 else 0)
    }

    private fun Cursor.toCatalogItem() = CatalogItem(
        id = long(COL_ID),
        name = string(Cat.NAME),
        category = ItemCategory.fromName(string(Cat.CATEGORY)),
        weight = double(Cat.WEIGHT),
        costCp = long(Cat.COST_CP),
        description = string(Cat.DESCRIPTION),
        isCustom = int(Cat.IS_CUSTOM) == 1,
    )
}
