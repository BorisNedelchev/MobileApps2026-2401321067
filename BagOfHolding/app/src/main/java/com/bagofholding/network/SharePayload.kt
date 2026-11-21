package com.bagofholding.network

import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.data.model.Purse
import com.bagofholding.domain.InventoryLayout
import com.bagofholding.domain.PurseRules
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON document that one phone serves and the other downloads. */
@Serializable
data class SharePayload(
    val app: String = APP_ID,
    val version: Int = PROTOCOL_VERSION,
    val character: SharedCharacter,
    val items: List<SharedItem>,
) {
    companion object {
        const val APP_ID = "bag-of-holding"
        /**
         * v2: purse with five coin types, Powerful Build, item descriptions.
         * v3: storage compartments; items are sent in screen order.
         */
        const val PROTOCOL_VERSION = 3
    }
}

@Serializable
data class SharedCharacter(
    val name: String,
    val strength: Int = PlayerCharacter.DEFAULT_STRENGTH,
    val powerfulBuild: Boolean = false,
    val pp: Long = 0,
    val gp: Long = 0,
    val ep: Long = 0,
    val sp: Long = 0,
    val cp: Long = 0,
)

@Serializable
data class SharedItem(
    val name: String,
    val category: String = ItemCategory.OTHER.name,
    val quantity: Int = 1,
    val weight: Double = 0.0,
    val costCp: Long = 0,
    val description: String = "",
    val notes: String = "",
    val isContainer: Boolean = false,
    val capacity: Double = 0.0,
    val weightlessContents: Boolean = false,
)

class ShareFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ShareCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: SharePayload): String = json.encodeToString(SharePayload.serializer(), payload)

    @Throws(ShareFormatException::class)
    fun decode(text: String): SharePayload {
        // SerializationException and IllegalArgumentException both mean "not our JSON".
        val payload = runCatching { json.decodeFromString(SharePayload.serializer(), text) }
            .getOrElse { throw ShareFormatException("Not a Bag of Holding inventory", it) }
        val problem = when {
            payload.app != SharePayload.APP_ID -> "Unknown app: ${payload.app}"
            payload.version > SharePayload.PROTOCOL_VERSION ->
                "Inventory was shared by a newer app version (${payload.version})"
            else -> null
        }
        if (problem != null) throw ShareFormatException(problem)
        return payload
    }

    fun fromDomain(character: PlayerCharacter, items: List<InventoryItem>) = SharePayload(
        character = SharedCharacter(
            name = character.name,
            strength = character.strength,
            powerfulBuild = character.powerfulBuild,
            pp = character.purse.pp,
            gp = character.purse.gp,
            ep = character.purse.ep,
            sp = character.purse.sp,
            cp = character.purse.cp,
        ),
        items = InventoryLayout.ordered(items).map {
            SharedItem(
                name = it.name,
                category = it.category.name,
                quantity = it.quantity,
                weight = it.weight,
                costCp = it.costCp,
                description = it.description,
                notes = it.notes,
                isContainer = it.isContainer,
                capacity = it.capacity,
                weightlessContents = it.weightlessContents,
            )
        },
    )
}

fun SharedCharacter.toDomain(): PlayerCharacter = PlayerCharacter(
    name = name.trim().ifBlank { "Unnamed" },
    strength = strength.coerceIn(PlayerCharacter.MIN_STRENGTH, PlayerCharacter.MAX_STRENGTH),
    powerfulBuild = powerfulBuild,
    purse = Purse(
        pp = pp.coerceIn(0, PurseRules.MAX_COINS),
        gp = gp.coerceIn(0, PurseRules.MAX_COINS),
        ep = ep.coerceIn(0, PurseRules.MAX_COINS),
        sp = sp.coerceIn(0, PurseRules.MAX_COINS),
        cp = cp.coerceIn(0, PurseRules.MAX_COINS),
    ),
)

fun SharedItem.toDomain(characterId: Long): InventoryItem = InventoryItem(
    characterId = characterId,
    name = name.trim(),
    category = ItemCategory.fromName(category),
    quantity = quantity,
    weight = weight.coerceAtLeast(0.0),
    costCp = costCp.coerceAtLeast(0),
    description = description,
    notes = notes,
    isContainer = isContainer,
    capacity = capacity.coerceAtLeast(0.0),
    weightlessContents = isContainer && weightlessContents,
)
