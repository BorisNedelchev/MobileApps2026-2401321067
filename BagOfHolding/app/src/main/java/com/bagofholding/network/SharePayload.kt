package com.bagofholding.network

import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
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
        const val PROTOCOL_VERSION = 1
    }
}

@Serializable
data class SharedCharacter(
    val name: String,
    val characterClass: String = "",
    val race: String = "",
    val level: Int = 1,
    val strength: Int = PlayerCharacter.DEFAULT_STRENGTH,
    val purseCp: Long = 0,
)

@Serializable
data class SharedItem(
    val name: String,
    val category: String = ItemCategory.OTHER.name,
    val quantity: Int = 1,
    val weight: Double = 0.0,
    val costCp: Long = 0,
    val notes: String = "",
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
            characterClass = character.characterClass,
            race = character.race,
            level = character.level,
            strength = character.strength,
            purseCp = character.purseCp,
        ),
        items = items.map {
            SharedItem(
                name = it.name,
                category = it.category.name,
                quantity = it.quantity,
                weight = it.weight,
                costCp = it.costCp,
                notes = it.notes,
            )
        },
    )
}

fun SharedCharacter.toDomain(): PlayerCharacter = PlayerCharacter(
    name = name.trim().ifBlank { "Unnamed" },
    characterClass = characterClass,
    race = race,
    level = level.coerceIn(PlayerCharacter.MIN_LEVEL, PlayerCharacter.MAX_LEVEL),
    strength = strength.coerceIn(PlayerCharacter.MIN_STRENGTH, PlayerCharacter.MAX_STRENGTH),
    purseCp = purseCp.coerceAtLeast(0),
)

fun SharedItem.toDomain(characterId: Long): InventoryItem = InventoryItem(
    characterId = characterId,
    name = name.trim(),
    category = ItemCategory.fromName(category),
    quantity = quantity,
    weight = weight.coerceAtLeast(0.0),
    costCp = costCp.coerceAtLeast(0),
    notes = notes,
)
