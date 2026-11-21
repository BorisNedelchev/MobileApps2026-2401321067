package com.bagofholding.domain

import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter

/** Outcome of validating a form: either the parsed value or the set of invalid fields. */
sealed interface FormResult<out T, F> {
    data class Valid<T, F>(val value: T) : FormResult<T, F>
    data class Invalid<F>(val errors: Set<F>) : FormResult<Nothing, F>
}

/** Raw text typed into the character dialog. */
data class CharacterInput(
    val name: String,
    val characterClass: String,
    val race: String,
    val level: String,
    val strength: String,
    val gold: String,
    val silver: String,
    val copper: String,
)

enum class CharacterField { NAME, LEVEL, STRENGTH, GOLD, SILVER, COPPER }

object CharacterValidator {
    const val MAX_NAME_LENGTH = 60

    fun validate(id: Long, input: CharacterInput): FormResult<PlayerCharacter, CharacterField> {
        val errors = mutableSetOf<CharacterField>()
        val name = input.name.trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) errors += CharacterField.NAME

        val level = input.level.trim().ifEmpty { "1" }.toIntOrNull()
        if (level == null || level !in PlayerCharacter.MIN_LEVEL..PlayerCharacter.MAX_LEVEL) {
            errors += CharacterField.LEVEL
        }
        val strength = input.strength.trim().ifEmpty { PlayerCharacter.DEFAULT_STRENGTH.toString() }.toIntOrNull()
        if (strength == null || strength !in PlayerCharacter.MIN_STRENGTH..PlayerCharacter.MAX_STRENGTH) {
            errors += CharacterField.STRENGTH
        }
        val gp = parseCoins(input.gold, CharacterField.GOLD, errors)
        val sp = parseCoins(input.silver, CharacterField.SILVER, errors)
        val cp = parseCoins(input.copper, CharacterField.COPPER, errors)

        if (errors.isNotEmpty() || level == null || strength == null) return FormResult.Invalid(errors)
        return FormResult.Valid(
            PlayerCharacter(
                id = id,
                name = name,
                characterClass = input.characterClass.trim(),
                race = input.race.trim(),
                level = level,
                strength = strength,
                purseCp = Currency.toCopper(gp, sp, cp),
            ),
        )
    }

    private fun parseCoins(text: String, field: CharacterField, errors: MutableSet<CharacterField>): Long {
        val value = text.trim().ifEmpty { "0" }.toLongOrNull()
        if (value == null || value < 0 || value > MAX_COINS) {
            errors += field
            return 0
        }
        return value
    }

    private const val MAX_COINS = 10_000_000L
}

/** Raw text typed into the item dialog. */
data class ItemInput(
    val name: String,
    val category: ItemCategory,
    val quantity: String,
    val weight: String,
    val costGold: String,
    val notes: String,
)

enum class ItemField { NAME, QUANTITY, WEIGHT, COST }

object ItemValidator {
    const val MAX_NAME_LENGTH = 80
    private const val MAX_WEIGHT = 100_000.0

    fun validate(base: InventoryItem, input: ItemInput): FormResult<InventoryItem, ItemField> {
        val errors = mutableSetOf<ItemField>()
        val name = input.name.trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) errors += ItemField.NAME

        val quantity = input.quantity.trim().ifEmpty { "1" }.toIntOrNull()
        if (quantity == null || quantity !in 1..InventoryRules.MAX_QUANTITY) errors += ItemField.QUANTITY

        val weight = input.weight.trim().replace(',', '.').ifEmpty { "0" }.toDoubleOrNull()
        if (weight == null || weight < 0 || weight > MAX_WEIGHT) errors += ItemField.WEIGHT

        val cost = Currency.parseGold(input.costGold)
        if (cost == null) errors += ItemField.COST

        // A null value always comes with an entry in errors; the null checks only enable smart casts.
        if (quantity == null || weight == null || cost == null) return FormResult.Invalid(errors)
        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        return FormResult.Valid(
            base.copy(
                name = name,
                category = input.category,
                quantity = quantity,
                weight = weight,
                costCp = cost,
                notes = input.notes.trim(),
            ),
        )
    }
}
