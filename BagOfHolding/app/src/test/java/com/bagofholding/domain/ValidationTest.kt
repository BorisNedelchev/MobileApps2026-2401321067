package com.bagofholding.domain

import com.bagofholding.data.model.InventoryItem
import com.bagofholding.data.model.ItemCategory
import com.bagofholding.data.model.PlayerCharacter
import com.bagofholding.data.model.Purse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterValidatorTest {

    private val blank = PlayerCharacter(name = "")

    @Test
    fun `valid input produces a trimmed character`() {
        val input = CharacterInput(name = "  Grog ", strength = "18", powerfulBuild = true)
        val result = CharacterValidator.validate(blank, input)
        assertTrue(result is FormResult.Valid)
        val character = (result as FormResult.Valid).value
        assertEquals("Grog", character.name)
        assertEquals(18, character.strength)
        assertTrue(character.powerfulBuild)
    }

    @Test
    fun `editing keeps the id and the purse`() {
        val existing = PlayerCharacter(id = 3, name = "Ana", purse = Purse(gp = 40))
        val result = CharacterValidator.validate(existing, CharacterInput("Ana", "12", false)) as FormResult.Valid
        assertEquals(3L, result.value.id)
        assertEquals(Purse(gp = 40), result.value.purse)
    }

    @Test
    fun `invalid fields are all reported`() {
        val result = CharacterValidator.validate(blank, CharacterInput(" ", "31", false))
        assertEquals(setOf(CharacterField.NAME, CharacterField.STRENGTH), (result as FormResult.Invalid).errors)
        val letters = CharacterValidator.validate(blank, CharacterInput("Bo", "x", false)) as FormResult.Invalid
        assertEquals(setOf(CharacterField.STRENGTH), letters.errors)
    }

    @Test
    fun `empty strength falls back to the default`() {
        val result = CharacterValidator.validate(blank, CharacterInput("Bo", "", false)) as FormResult.Valid
        assertEquals(PlayerCharacter.DEFAULT_STRENGTH, result.value.strength)
    }
}

class ItemValidatorTest {

    private val base = InventoryItem(characterId = 1, name = "")

    @Test
    fun `valid item input is parsed`() {
        val input = ItemInput("Rope", ItemCategory.GEAR, "2", "10,5", "1.5", " Hempen. ", " 50 ft ")
        val result = ItemValidator.validate(base, input) as FormResult.Valid
        assertEquals("Rope", result.value.name)
        assertEquals(2, result.value.quantity)
        assertEquals(10.5, result.value.weight, 0.001)
        assertEquals(150L, result.value.costCp)
        assertEquals("Hempen.", result.value.description)
        assertEquals("50 ft", result.value.notes)
    }

    @Test
    fun `invalid item input reports fields`() {
        val input = ItemInput("", ItemCategory.GEAR, "0", "-3", "abc", "", "")
        val result = ItemValidator.validate(base, input) as FormResult.Invalid
        assertEquals(setOf(ItemField.NAME, ItemField.QUANTITY, ItemField.WEIGHT, ItemField.COST), result.errors)
    }
}
