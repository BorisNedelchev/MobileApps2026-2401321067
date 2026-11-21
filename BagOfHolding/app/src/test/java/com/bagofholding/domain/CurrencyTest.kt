package com.bagofholding.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyTest {

    @Test
    fun `toCopper combines all coins`() {
        assertEquals(1253L, Currency.toCopper(gp = 12, sp = 5, cp = 3))
    }

    @Test
    fun `split is the inverse of toCopper`() {
        assertEquals(Coins(12, 5, 3), Currency.split(1253))
        assertEquals(Coins(0, 0, 0), Currency.split(-50))
    }

    @Test
    fun `format skips zero parts`() {
        assertEquals("12 gp 5 sp 3 cp", Currency.format(1253))
        assertEquals("15 gp", Currency.format(1500))
        assertEquals("5 sp", Currency.format(50))
        assertEquals("0 gp", Currency.format(0))
    }

    @Test
    fun `parseGold accepts dot and comma decimals`() {
        assertEquals(1500L, Currency.parseGold("15"))
        assertEquals(50L, Currency.parseGold("0.5"))
        assertEquals(125L, Currency.parseGold("1,25"))
        assertEquals(0L, Currency.parseGold("  "))
    }

    @Test
    fun `parseGold rejects garbage and negatives`() {
        assertNull(Currency.parseGold("abc"))
        assertNull(Currency.parseGold("-1"))
    }

    @Test
    fun `toGoldInput produces short editable text`() {
        assertEquals("15", Currency.toGoldInput(1500))
        assertEquals("15.5", Currency.toGoldInput(1550))
        assertEquals("0.05", Currency.toGoldInput(5))
    }
}
