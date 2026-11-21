package com.bagofholding.data.model

/**
 * A player character. Only what the inventory needs is stored: Strength and the
 * Powerful Build trait decide the carrying capacity; the purse holds the five D&D coin types.
 */
data class PlayerCharacter(
    val id: Long = 0,
    val name: String,
    val strength: Int = DEFAULT_STRENGTH,
    /** Powerful Build (e.g. goliath, firbolg): counts as one size larger, so capacity is doubled. */
    val powerfulBuild: Boolean = false,
    val purse: Purse = Purse(),
) {
    companion object {
        const val DEFAULT_STRENGTH = 10
        const val MIN_STRENGTH = 1
        const val MAX_STRENGTH = 30
    }
}

/** D&D 5e coins and their value in copper pieces: 1 pp = 10 gp = 20 ep = 100 sp = 1000 cp. */
enum class Coin(val copperValue: Long) {
    PP(1_000),
    GP(100),
    EP(50),
    SP(10),
    CP(1),
}

/** The coins a character carries. Coins are kept separately (no automatic exchange). */
data class Purse(
    val pp: Long = 0,
    val gp: Long = 0,
    val ep: Long = 0,
    val sp: Long = 0,
    val cp: Long = 0,
) {
    operator fun get(coin: Coin): Long = when (coin) {
        Coin.PP -> pp
        Coin.GP -> gp
        Coin.EP -> ep
        Coin.SP -> sp
        Coin.CP -> cp
    }

    fun with(coin: Coin, amount: Long): Purse = when (coin) {
        Coin.PP -> copy(pp = amount)
        Coin.GP -> copy(gp = amount)
        Coin.EP -> copy(ep = amount)
        Coin.SP -> copy(sp = amount)
        Coin.CP -> copy(cp = amount)
    }

    /** Total worth of all coins in copper pieces. */
    val totalCp: Long get() = Coin.entries.sumOf { this[it] * it.copperValue }
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
    /** What the item is / does (copied from the item database). */
    val description: String = "",
    /** The player's own notes, e.g. "found in the goblin cave". */
    val notes: String = "",
    val catalogId: Long? = null,
    /** Position in the inventory list (drag & drop). Lower comes first. */
    val sortOrder: Int = 0,
    /**
     * A storage compartment (backpack, belt, bag of holding…). Every item listed below it,
     * up to the next compartment, is inside it. See [com.bagofholding.domain.InventoryLayout].
     */
    val isContainer: Boolean = false,
    /** How many pounds the compartment holds; 0 = no limit. */
    val capacity: Double = 0.0,
    /** Contents do not count toward carried weight (e.g. Bag of Holding). */
    val weightlessContents: Boolean = false,
    /** Contents are hidden behind the compartment in the list. */
    val collapsed: Boolean = false,
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
    /** > 0 makes the item a storage compartment when added to an inventory (pounds it holds). */
    val capacity: Double = 0.0,
    val weightlessContents: Boolean = false,
) {
    val isContainer: Boolean get() = capacity > 0
}

fun CatalogItem.toInventoryItem(characterId: Long, quantity: Int = 1): InventoryItem = InventoryItem(
    characterId = characterId,
    name = name,
    category = category,
    quantity = quantity,
    weight = weight,
    costCp = costCp,
    description = description,
    catalogId = if (id > 0) id else null,
    isContainer = isContainer,
    capacity = capacity,
    weightlessContents = weightlessContents,
)

fun InventoryItem.toCatalogItem(): CatalogItem = CatalogItem(
    name = name,
    category = category,
    weight = weight,
    costCp = costCp,
    description = description,
    isCustom = true,
    // In the database a capacity > 0 is what makes an entry a storage compartment.
    capacity = capacity,
    weightlessContents = capacity > 0 && weightlessContents,
)
