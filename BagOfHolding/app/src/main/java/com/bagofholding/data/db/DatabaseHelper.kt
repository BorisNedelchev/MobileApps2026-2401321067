package com.bagofholding.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Creates the SQLite database on first launch and fills the item catalog
 * with the built-in item list from [CatalogSeed].
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(
        context.applicationContext,
        DbContract.DATABASE_NAME,
        null,
        DbContract.DATABASE_VERSION,
    ) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Needed for ON DELETE CASCADE (deleting a character deletes its items).
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(DbContract.Characters.CREATE)
        db.execSQL(DbContract.Catalog.CREATE)
        db.execSQL(DbContract.Inventory.CREATE)
        db.execSQL(DbContract.Inventory.CREATE_INDEX)
        seedCatalog(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the only schema so far. Future versions should migrate with ALTER TABLE
        // instead of dropping user data.
        db.execSQL("DROP TABLE IF EXISTS ${DbContract.Inventory.TABLE}")
        db.execSQL("DROP TABLE IF EXISTS ${DbContract.Catalog.TABLE}")
        db.execSQL("DROP TABLE IF EXISTS ${DbContract.Characters.TABLE}")
        onCreate(db)
    }

    private fun seedCatalog(db: SQLiteDatabase) {
        // onCreate already runs inside a transaction, so this is a single atomic write.
        CatalogSeed.items.forEach { item ->
            val values = ContentValues().apply {
                put(DbContract.Catalog.NAME, item.name)
                put(DbContract.Catalog.CATEGORY, item.category.name)
                put(DbContract.Catalog.WEIGHT, item.weight)
                put(DbContract.Catalog.COST_CP, item.costCp)
                put(DbContract.Catalog.DESCRIPTION, item.description)
                put(DbContract.Catalog.IS_CUSTOM, 0)
            }
            db.insertOrThrow(DbContract.Catalog.TABLE, null, values)
        }
    }
}

internal fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
internal fun Cursor.double(column: String): Double = getDouble(getColumnIndexOrThrow(column))
internal fun Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getLong(index)
}

internal inline fun <T> Cursor.mapRows(mapper: (Cursor) -> T): List<T> = use { cursor ->
    val result = ArrayList<T>(cursor.count)
    while (cursor.moveToNext()) result.add(mapper(cursor))
    result
}
