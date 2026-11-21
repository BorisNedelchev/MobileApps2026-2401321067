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

    /** Step-by-step migrations keep the player's characters and items when the app is updated. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateToV2(db)
        if (oldVersion < 3) migrateToV3(db)
    }

    /**
     * v1 -> v2: the purse becomes five coin columns, Powerful Build is added and inventory items
     * get a description. The v1 columns character_class, race, level and purse_cp stay in the table
     * (SQLite on API 24 cannot drop columns) but are no longer used; they all have defaults.
     */
    private fun migrateToV2(db: SQLiteDatabase) {
        val ch = DbContract.Characters
        val inv = DbContract.Inventory
        val cat = DbContract.Catalog
        db.execSQL("ALTER TABLE ${ch.TABLE} ADD COLUMN ${ch.POWERFUL_BUILD} INTEGER NOT NULL DEFAULT 0")
        listOf(ch.PP, ch.GP, ch.EP, ch.SP, ch.CP).forEach { column ->
            db.execSQL("ALTER TABLE ${ch.TABLE} ADD COLUMN $column INTEGER NOT NULL DEFAULT 0")
        }
        db.execSQL(
            "UPDATE ${ch.TABLE} SET ${ch.GP} = ${ch.LEGACY_PURSE_CP} / 100, " +
                "${ch.SP} = (${ch.LEGACY_PURSE_CP} % 100) / 10, ${ch.CP} = ${ch.LEGACY_PURSE_CP} % 10",
        )
        db.execSQL("ALTER TABLE ${inv.TABLE} ADD COLUMN ${inv.DESCRIPTION} TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "UPDATE ${inv.TABLE} SET ${inv.DESCRIPTION} = COALESCE((SELECT c.${cat.DESCRIPTION} " +
                "FROM ${cat.TABLE} c WHERE c.${DbContract.COL_ID} = ${inv.TABLE}.${inv.CATALOG_ID}), '') " +
                "WHERE ${inv.CATALOG_ID} IS NOT NULL",
        )
    }

    /**
     * v2 -> v3: manual ordering and storage compartments. Existing items keep the order they had
     * on screen (category, then name). Containers go last so they start out empty, and built-in
     * bags (backpack, sack, pouch, bag of holding) become storage compartments.
     */
    private fun migrateToV3(db: SQLiteDatabase) {
        val inv = DbContract.Inventory
        val cat = DbContract.Catalog
        db.execSQL("ALTER TABLE ${inv.TABLE} ADD COLUMN ${inv.SORT_ORDER} INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ${inv.TABLE} ADD COLUMN ${inv.IS_CONTAINER} INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ${inv.TABLE} ADD COLUMN ${inv.CAPACITY} REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ${inv.TABLE} ADD COLUMN ${inv.WEIGHTLESS_CONTENTS} INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ${inv.TABLE} ADD COLUMN ${inv.COLLAPSED} INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ${cat.TABLE} ADD COLUMN ${cat.CAPACITY} REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ${cat.TABLE} ADD COLUMN ${cat.WEIGHTLESS_CONTENTS} INTEGER NOT NULL DEFAULT 0")

        CatalogSeed.items.filter { it.isContainer }.forEach { item ->
            db.execSQL(
                "UPDATE ${cat.TABLE} SET ${cat.CAPACITY} = ?, ${cat.WEIGHTLESS_CONTENTS} = ? " +
                    "WHERE ${cat.NAME} = ? AND ${cat.IS_CUSTOM} = 0",
                arrayOf<Any>(item.capacity, if (item.weightlessContents) 1 else 0, item.name),
            )
        }
        val fromCatalog = "FROM ${cat.TABLE} c WHERE c.${DbContract.COL_ID} = ${inv.TABLE}.${inv.CATALOG_ID}"
        db.execSQL(
            "UPDATE ${inv.TABLE} SET ${inv.IS_CONTAINER} = 1, " +
                "${inv.CAPACITY} = (SELECT c.${cat.CAPACITY} $fromCatalog), " +
                "${inv.WEIGHTLESS_CONTENTS} = (SELECT c.${cat.WEIGHTLESS_CONTENTS} $fromCatalog) " +
                "WHERE ${inv.CATALOG_ID} IN " +
                "(SELECT ${DbContract.COL_ID} FROM ${cat.TABLE} WHERE ${cat.CAPACITY} > 0)",
        )

        val ordered = db.rawQuery(
            "SELECT ${DbContract.COL_ID} FROM ${inv.TABLE} " +
                "ORDER BY ${inv.IS_CONTAINER}, ${inv.CATEGORY}, ${inv.NAME} COLLATE NOCASE, ${DbContract.COL_ID}",
            null,
        ).mapRows { it.long(DbContract.COL_ID) }
        ordered.forEachIndexed { index, id ->
            db.execSQL(
                "UPDATE ${inv.TABLE} SET ${inv.SORT_ORDER} = ? WHERE ${DbContract.COL_ID} = ?",
                arrayOf<Any>(index, id),
            )
        }
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
                put(DbContract.Catalog.CAPACITY, item.capacity)
                put(DbContract.Catalog.WEIGHTLESS_CONTENTS, if (item.weightlessContents) 1 else 0)
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
