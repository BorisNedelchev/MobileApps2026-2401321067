package com.bagofholding.data.db

/** Table and column names for the SQLite schema. */
object DbContract {
    const val DATABASE_NAME = "bag_of_holding.db"
    /**
     * v1: first release. v2: purse split into pp/gp/ep/sp/cp, Powerful Build, item descriptions.
     * v3: manual item order (drag & drop) and storage compartments.
     */
    const val DATABASE_VERSION = 3

    const val COL_ID = "_id"

    object Characters {
        const val TABLE = "characters"
        const val NAME = "name"
        const val STRENGTH = "strength"
        const val POWERFUL_BUILD = "powerful_build"
        const val PP = "pp"
        const val GP = "gp"
        const val EP = "ep"
        const val SP = "sp"
        const val CP = "cp"
        const val CREATED_AT = "created_at"

        /** Version 1 stored the whole purse in one column; only read by the v1 -> v2 migration. */
        const val LEGACY_PURSE_CP = "purse_cp"

        const val CREATE = """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $NAME TEXT NOT NULL,
                $STRENGTH INTEGER NOT NULL DEFAULT 10,
                $POWERFUL_BUILD INTEGER NOT NULL DEFAULT 0,
                $PP INTEGER NOT NULL DEFAULT 0,
                $GP INTEGER NOT NULL DEFAULT 0,
                $EP INTEGER NOT NULL DEFAULT 0,
                $SP INTEGER NOT NULL DEFAULT 0,
                $CP INTEGER NOT NULL DEFAULT 0,
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
        const val DESCRIPTION = "description"
        const val NOTES = "notes"
        const val CATALOG_ID = "catalog_id"
        const val SORT_ORDER = "sort_order"
        const val IS_CONTAINER = "is_container"
        const val CAPACITY = "capacity"
        const val WEIGHTLESS_CONTENTS = "weightless_contents"
        const val COLLAPSED = "collapsed"

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
                $DESCRIPTION TEXT NOT NULL DEFAULT '',
                $NOTES TEXT NOT NULL DEFAULT '',
                $CATALOG_ID INTEGER
                    REFERENCES ${Catalog.TABLE}($COL_ID) ON DELETE SET NULL,
                $SORT_ORDER INTEGER NOT NULL DEFAULT 0,
                $IS_CONTAINER INTEGER NOT NULL DEFAULT 0,
                $CAPACITY REAL NOT NULL DEFAULT 0,
                $WEIGHTLESS_CONTENTS INTEGER NOT NULL DEFAULT 0,
                $COLLAPSED INTEGER NOT NULL DEFAULT 0
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
        const val CAPACITY = "capacity"
        const val WEIGHTLESS_CONTENTS = "weightless_contents"

        const val CREATE = """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $NAME TEXT NOT NULL,
                $CATEGORY TEXT NOT NULL,
                $WEIGHT REAL NOT NULL DEFAULT 0,
                $COST_CP INTEGER NOT NULL DEFAULT 0,
                $DESCRIPTION TEXT NOT NULL DEFAULT '',
                $IS_CUSTOM INTEGER NOT NULL DEFAULT 0,
                $CAPACITY REAL NOT NULL DEFAULT 0,
                $WEIGHTLESS_CONTENTS INTEGER NOT NULL DEFAULT 0
            )
        """
    }
}
