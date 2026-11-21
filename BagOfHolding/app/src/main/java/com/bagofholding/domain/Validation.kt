package com.bagofholding.domain

import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter

/** Outcome of validating a form: either the parsed value or the set of invalid fields. */
sealed interface FormResult<out T, F> {
    data class Valid<T, F>(val value: T) : FormResult<T, F>
    data class Invalid<F>(val errors: Set<F>) : FormResult<Nothing, F>
}

/** Raw values from the character dialog. */
data class CharacterInput(
    val name: String,
    val strength: String,
    val powerfulBuild: Boolean,
)

enum class CharacterField { NAME, STRENGTH }

object CharacterValidator {
    const val MAX_NAME_LENGTH = 60

    /** Validates the form and applies it to [base] (a blank character or the one being edited). */
    fun validate(base: PlayerCharacter, input: CharacterInput): FormResult<PlayerCharacter, CharacterField> {
        val errors = mutableSetOf<CharacterField>()
        val name = input.name.trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) errors += CharacterField.NAME

        val strength = input.strength.trim().ifEmpty { PlayerCharacter.DEFAULT_STRENGTH.toString() }.toIntOrNull()
        if (strength == null || strength !in PlayerCharacter.MIN_STRENGTH..PlayerCharacter.MAX_STRENGTH) {
            errors += CharacterField.STRENGTH
        }

        if (errors.isNotEmpty() || strength == null) return FormResult.Invalid(errors)
        return FormResult.Valid(base.copy(name = name, strength = strength, powerfulBuild = input.powerfulBuild))
    }
}

/** Raw text typed into the item dialog. */
data class ItemInput(
    val name: String,
    val category: ItemCategory,
    val quantity: String,
    val weight: String,
    val costGold: String,
    val description: String,
    val notes: String,
    /** Storage compartments only: pounds it holds ("" = no limit / not a compartment). */
    val capacity: String = "",
    val weightlessContents: Boolean = false,
)

enum class ItemField { NAME, QUANTITY, WEIGHT, COST, CAPACITY }

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

        val capacity = parsePounds(input.capacity)
        if (capacity == null) errors += ItemField.CAPACITY

        // A null value always comes with an entry in errors; the null checks only enable smart casts.
        if (quantity == null || weight == null) return FormResult.Invalid(errors)
        if (cost == null || capacity == null) return FormResult.Invalid(errors)
        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        return FormResult.Valid(
            base.copy(
                name = name,
                category = input.category,
                quantity = quantity,
                weight = weight,
                costCp = cost,
                description = input.description.trim(),
                notes = input.notes.trim(),
                capacity = capacity,
                weightlessContents = input.weightlessContents,
            ),
        )
    }

    private fun parsePounds(text: String): Double? {
        val value = text.trim().replace(',', '.').ifEmpty { "0" }.toDoubleOrNull()
        return value?.takeIf { it >= 0 && it <= MAX_WEIGHT }
    }
}
