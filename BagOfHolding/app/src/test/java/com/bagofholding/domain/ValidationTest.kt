package com.bagofholding.domain

import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterValidatorTest {

    private fun input(
        name: String = "Thorin",
        level: String = "5",
        strength: String = "16",
        gold: String = "12",
        silver: String = "",
        copper: String = "",
    ) = CharacterInput(name, " Fighter ", "Dwarf", level, strength, gold, silver, copper)

    @Test
    fun `valid input produces a trimmed character with purse in copper`() {
        val result = CharacterValidator.validate(id = 3, input = input(name = "  Thorin "))
        assertTrue(result is FormResult.Valid)
        val character = (result as FormResult.Valid).value
        assertEquals(3L, character.id)
        assertEquals("Thorin", character.name)
        assertEquals("Fighter", character.characterClass)
        assertEquals(1200L, character.purseCp)
    }

    @Test
    fun `invalid fields are all reported`() {
        val result = CharacterValidator.validate(0, input(name = " ", level = "25", strength = "x", copper = "-1"))
        assertTrue(result is FormResult.Invalid)
        assertEquals(
            setOf(CharacterField.NAME, CharacterField.LEVEL, CharacterField.STRENGTH, CharacterField.COPPER),
            (result as FormResult.Invalid).errors,
        )
    }

    @Test
    fun `empty level and strength fall back to defaults`() {
        val result = CharacterValidator.validate(0, input(level = "", strength = "")) as FormResult.Valid
        assertEquals(1, result.value.level)
        assertEquals(10, result.value.strength)
    }
}

class ItemValidatorTest {

    private val base = InventoryItem(characterId = 1, name = "")

    @Test
    fun `valid item input is parsed`() {
        val input = ItemInput("Rope", ItemCategory.GEAR, "2", "10,5", "1.5", " 50 ft ")
        val result = ItemValidator.validate(base, input) as FormResult.Valid
        assertEquals("Rope", result.value.name)
        assertEquals(2, result.value.quantity)
        assertEquals(10.5, result.value.weight, 0.001)
        assertEquals(150L, result.value.costCp)
        assertEquals("50 ft", result.value.notes)
    }

    @Test
    fun `invalid item input reports fields`() {
        val input = ItemInput("", ItemCategory.GEAR, "0", "-3", "abc", "")
        val result = ItemValidator.validate(base, input) as FormResult.Invalid
        assertEquals(setOf(ItemField.NAME, ItemField.QUANTITY, ItemField.WEIGHT, ItemField.COST), result.errors)
    }
}
