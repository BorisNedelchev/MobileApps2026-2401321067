package com.bagofholding.domain

import com.bagofholding.data.model.Coin
import com.bagofholding.data.model.Purse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyTest {

    @Test
    fun `format skips zero parts`() {
        assertEquals("12 gp 5 sp 3 cp", Currency.format(1253))
        assertEquals("15 gp", Currency.format(1500))
        assertEquals("5 sp", Currency.format(50))
        assertEquals("0 gp", Currency.format(0))
        assertEquals("0 gp", Currency.format(-50))
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

    @Test
    fun `formatPurse lists only coins the character has`() {
        assertEquals("2 pp, 12 gp, 5 sp", Currency.formatPurse(Purse(pp = 2, gp = 12, sp = 5)))
        assertEquals("no coins", Currency.formatPurse(Purse()))
    }
}

class PurseTest {

    @Test
    fun `total value uses the 5e exchange rates`() {
        // 1 pp = 1000 cp, 1 gp = 100 cp, 1 ep = 50 cp, 1 sp = 10 cp
        val purse = Purse(pp = 1, gp = 2, ep = 3, sp = 4, cp = 5)
        assertEquals(1000L + 200 + 150 + 40 + 5, purse.totalCp)
    }

    @Test
    fun `get and with address each coin`() {
        var purse = Purse()
        Coin.entries.forEachIndexed { index, coin -> purse = purse.with(coin, index + 1L) }
        assertEquals(Purse(pp = 1, gp = 2, ep = 3, sp = 4, cp = 5), purse)
        assertEquals(3L, purse[Coin.EP])
    }

    @Test
    fun `add remove and set coins`() {
        assertEquals(15L, PurseRules.apply(10, CoinOperation.ADD, 5))
        assertEquals(4L, PurseRules.apply(10, CoinOperation.REMOVE, 6))
        assertEquals(0L, PurseRules.apply(10, CoinOperation.REMOVE, 10))
        assertEquals(42L, PurseRules.apply(10, CoinOperation.SET, 42))
    }

    @Test
    fun `cannot pay more than you have or use negative amounts`() {
        assertNull(PurseRules.apply(10, CoinOperation.REMOVE, 11))
        assertNull(PurseRules.apply(10, CoinOperation.ADD, -1))
        assertNull(PurseRules.apply(PurseRules.MAX_COINS, CoinOperation.ADD, 1))
    }
}
