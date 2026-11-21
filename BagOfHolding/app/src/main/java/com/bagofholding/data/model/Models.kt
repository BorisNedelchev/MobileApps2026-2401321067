package com.bagofholding.data.model

/** A player character. Money is stored in copper pieces (1 gp = 10 sp = 100 cp). */
data class PlayerCharacter(
    val id: Long = 0,
    val name: String,
    val characterClass: String = "",
    val race: String = "",
    val level: Int = 1,
    val strength: Int = DEFAULT_STRENGTH,
    val purseCp: Long = 0,
) {
    companion object {
        const val DEFAULT_STRENGTH = 10
        const val MIN_LEVEL = 1
        const val MAX_LEVEL = 20
        const val MIN_STRENGTH = 1
        const val MAX_STRENGTH = 30
    }
}

/** A character plus a few aggregates shown on the character list. */
data class CharacterWithStats(
    val character: PlayerCharacter,
    val itemCount: Int,
    val totalWeight: Double,
)

enum class ItemCategory {
    WEAPON,
    ARMOR,
    GEAR,
    TOOL,
    CONSUMABLE,
    MAGIC,
    TREASURE,
    OTHER,
    ;

    companion object {
        /** Lenient parsing used for database rows and data received over the network. */
        fun fromName(name: String?): ItemCategory =
            ItemCategory.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: OTHER
    }
}

/**
 * One stack of items in a character's inventory. Item data is copied from the catalog
 * so that the player can rename/edit it freely and so that shared inventories work
 * even when the receiving phone does not have the same catalog entries.
 */
data class InventoryItem(
    val id: Long = 0,
    val characterId: Long,
    val name: String,
    val category: ItemCategory = ItemCategory.OTHER,
    val quantity: Int = 1,
    /** Weight of ONE unit in pounds. */
    val weight: Double = 0.0,
    /** Cost of ONE unit in copper pieces. */
    val costCp: Long = 0,
    val notes: String = "",
    val catalogId: Long? = null,
)

/** An entry of the item database (built-in SRD items or user-created custom items). */
data class CatalogItem(
    val id: Long = 0,
    val name: String,
    val category: ItemCategory = ItemCategory.OTHER,
    val weight: Double = 0.0,
    val costCp: Long = 0,
    val description: String = "",
    val isCustom: Boolean = false,
)

fun CatalogItem.toInventoryItem(characterId: Long, quantity: Int = 1): InventoryItem = InventoryItem(
    characterId = characterId,
    name = name,
    category = category,
    quantity = quantity,
    weight = weight,
    costCp = costCp,
    notes = "",
    catalogId = if (id > 0) id else null,
)

fun InventoryItem.toCatalogItem(): CatalogItem = CatalogItem(
    name = name,
    category = category,
    weight = weight,
    costCp = costCp,
    description = notes,
    isCustom = true,
)
