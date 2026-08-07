package com.btc.shops.api

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the invariant the shop system leaked for a while: a purchase must never settle against a
 * balance it cannot cover, and an unreadable balance must refuse rather than read as zero.
 */
class BalanceParserTest {
    @Test
    fun `reads plain numbers`() {
        assertEquals(0.0, BalanceParser.parse("0"))
        assertEquals(42.0, BalanceParser.parse("42"))
        assertEquals(12.5, BalanceParser.parse("12.5"))
    }

    @Test
    fun `reads currency decorated amounts`() {
        assertEquals(1234.56, BalanceParser.parse("$1,234.56"))
        assertEquals(1234.56, BalanceParser.parse("1 234,56 EUR"))
        assertEquals(1234.56, BalanceParser.parse("1.234,56"))
        assertEquals(1234.0, BalanceParser.parse("1,234"))
        assertEquals(12.34, BalanceParser.parse("12,34"))
    }

    @Test
    fun `reads no-break and narrow spaces as grouping`() {
        assertEquals(1234.56, BalanceParser.parse("1 234,56"))
        assertEquals(1234.56, BalanceParser.parse("1 234,56"))
    }

    @Test
    fun `reads magnitude suffixes`() {
        assertEquals(12_500.0, BalanceParser.parse("12.5k"))
        assertEquals(3_000_000.0, BalanceParser.parse("3M"))
    }

    @Test
    fun `reads negative amounts`() {
        assertEquals(-25.0, BalanceParser.parse("-25"))
        assertEquals(-25.0, BalanceParser.parse("(25)"))
    }

    @Test
    fun `returns null rather than zero when unreadable`() {
        assertNull(BalanceParser.parse(null))
        assertNull(BalanceParser.parse(""))
        assertNull(BalanceParser.parse("   "))
        assertNull(BalanceParser.parse("N/A"))
        assertNull(BalanceParser.parse("%vault_eco_balance%"))
    }
}

class EconomyAffordabilityTest {
    private val player: UUID = UUID.randomUUID()

    /** Minimal economy exercising only the interface's default affordability logic. */
    private class FakeEconomy(private var balance: Double?) : Economy {
        var withdrawn: Double = 0.0
            private set

        override fun balanceOrNull(playerId: UUID): Double? = balance

        override fun withdraw(playerId: UUID, amount: Double): Boolean {
            if (!canAfford(playerId, amount)) return false
            balance = (balance ?: return false) - amount
            withdrawn += amount
            return true
        }

        override fun deposit(playerId: UUID, amount: Double) {
            balance = (balance ?: 0.0) + amount
        }
    }

    @Test
    fun `refuses when the balance is short`() {
        val economy = FakeEconomy(50.0)
        assertFalse(economy.canAfford(player, 50.01))
        assertFalse(economy.withdraw(player, 50.01))
        assertEquals(0.0, economy.withdrawn)
        assertEquals(50.0, economy.getBalance(player))
    }

    @Test
    fun `allows an exact-change purchase`() {
        val economy = FakeEconomy(50.0)
        assertTrue(economy.canAfford(player, 50.0))
        assertTrue(economy.withdraw(player, 50.0))
        assertEquals(0.0, economy.getBalance(player))
    }

    @Test
    fun `absorbs floating point noise on exact change`() {
        val economy = FakeEconomy(0.1 + 0.2)
        assertTrue(economy.canAfford(player, 0.3))
    }

    @Test
    fun `refuses everything when the balance is unknown`() {
        val economy = FakeEconomy(null)
        assertFalse(economy.canAfford(player, 1.0))
        assertFalse(economy.withdraw(player, 1.0))
        // getBalance still reports 0 for display, which is exactly why it must not gate purchases.
        assertEquals(0.0, economy.getBalance(player))
    }

    @Test
    fun `a free item stays free even without a readable balance`() {
        val economy = FakeEconomy(null)
        assertTrue(economy.canAfford(player, 0.0))
    }
}
