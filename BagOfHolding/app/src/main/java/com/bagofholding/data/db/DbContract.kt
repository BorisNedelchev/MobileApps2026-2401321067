package com.bagofholding.data.db

/** Table and column names for the SQLite schema. */
object DbContract {
    const val DATABASE_NAME = "bag_of_holding.db"
    const val DATABASE_VERSION = 1

    const val COL_ID = "_id"

    object Characters {
        const val TABLE = "characters"
        const val NAME = "name"
        const val CLASS = "character_class"
        const val RACE = "race"
        const val LEVEL = "level"
        const val STRENGTH = "strength"
        const val PURSE_CP = "purse_cp"
        const val CREATED_AT = "created_at"

        const val CREATE = """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $NAME TEXT NOT NULL,
                $CLASS TEXT NOT NULL DEFAULT '',
                $RACE TEXT NOT NULL DEFAULT '',
                $LEVEL INTEGER NOT NULL DEFAULT 1,
                $STRENGTH INTEGER NOT NULL DEFAULT 10,
                $PURSE_CP INTEGER NOT NULL DEFAULT 0,
                $CREATED_AT INTEGER NOT NULL
            )
        """
    }

    object Inventory {
        const val TABLE = "inventory_items"
        const val CHARACTER_ID = "character_id"
        const val NAME = "name"
        const val CATEGORY = "category"
        const val QUANTITY = "quantity"
        const val WEIGHT = "weight"
        const val COST_CP = "cost_cp"
        const val NOTES = "notes"
        const val CATALOG_ID = "catalog_id"

        const val CREATE = """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $CHARACTER_ID INTEGER NOT NULL
                    REFERENCES ${Characters.TABLE}($COL_ID) ON DELETE CASCADE,
                $NAME TEXT NOT NULL,
                $CATEGORY TEXT NOT NULL,
                $QUANTITY INTEGER NOT NULL DEFAULT 1,
                $WEIGHT REAL NOT NULL DEFAULT 0,
                $COST_CP INTEGER NOT NULL DEFAULT 0,
                $NOTES TEXT NOT NULL DEFAULT '',
                $CATALOG_ID INTEGER
                    REFERENCES ${Catalog.TABLE}($COL_ID) ON DELETE SET NULL
            )
        """

        const val CREATE_INDEX =
            "CREATE INDEX idx_inventory_character ON $TABLE($CHARACTER_ID)"
    }

    object Catalog {
        const val TABLE = "catalog_items"
        const val NAME = "name"
        const val CATEGORY = "category"
        const val WEIGHT = "weight"
        const val COST_CP = "cost_cp"
        const val DESCRIPTION = "description"
        const val IS_CUSTOM = "is_custom"

        const val CREATE = """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $NAME TEXT NOT NULL,
                $CATEGORY TEXT NOT NULL,
                $WEIGHT REAL NOT NULL DEFAULT 0,
                $COST_CP INTEGER NOT NULL DEFAULT 0,
                $DESCRIPTION TEXT NOT NULL DEFAULT '',
                $IS_CUSTOM INTEGER NOT NULL DEFAULT 0
            )
        """
    }
}
