package com.btc.shops.service

import com.btc.shops.manifest.PriceStrategy
import com.btc.shops.manifest.ShopArtifact
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the per-player stock of a shop: each player trades against their own, and a reset
 * refills everyone's without touching the shared stock's keys.
 */
class PerPlayerStockTest {

    /** The artifact kept in memory: same keys and same data as production, without the asset manager. */
    private class InMemoryArtifact : ShopArtifactPersistenceService() {
        private val data = ShopArtifact.ShopArtifactData()
        override fun load() = data
        override fun update(block: (ShopArtifact.ShopArtifactData) -> Unit) = block(data)
    }

    private val artifact = InMemoryArtifact()
    private val persistence = ShopPersistenceService(artifact)
    private val stock = StockService(persistence)

    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val limited = PriceStrategy(stockMax = 10)

    @Test
    fun `a purchase lowers the buyer's own stock and no one else's`() {
        stock.buy("shop", 0, 4, limited, playerUuid = alice)

        assertEquals(6, stock.getStock("shop", 0, 10, alice))
        assertEquals(10, stock.getStock("shop", 0, 10, bob))
        assertEquals(10, stock.getStock("shop", 0, 10, playerUuid = null))
    }

    @Test
    fun `a shared purchase leaves every player's own stock alone`() {
        stock.buy("shop", 0, 3, limited, playerUuid = null)

        assertEquals(7, stock.getStock("shop", 0, 10, playerUuid = null))
        assertEquals(10, stock.getStock("shop", 0, 10, alice))
    }

    @Test
    fun `selling back refills only the seller's own stock`() {
        stock.buy("shop", 0, 5, limited, alice)
        stock.buy("shop", 0, 5, limited, bob)

        stock.sell("shop", 0, 2, limited, alice)

        assertEquals(7, stock.getStock("shop", 0, 10, alice))
        assertEquals(5, stock.getStock("shop", 0, 10, bob))
    }

    @Test
    fun `resetting a per-player shop refills every player but not another shop`() {
        stock.buy("shop", 0, 5, limited, alice)
        stock.buy("shop", 1, 6, limited, bob)
        stock.buy("shop2", 0, 7, limited, alice)

        stock.resetPlayers("shop")

        assertEquals(10, stock.getStock("shop", 0, 10, alice))
        assertEquals(10, stock.getStock("shop", 1, 10, bob))
        assertEquals(3, stock.getStock("shop2", 0, 10, alice))
    }

    @Test
    fun `resetting the players' stocks keeps the shared stock and the limits`() {
        stock.buy("shop", 0, 4, limited, playerUuid = null)
        persistence.setPlayerLimit("shop", 0, alice, 2)

        stock.resetPlayers("shop")

        assertEquals(6, stock.getStock("shop", 0, 10, playerUuid = null))
        assertEquals(2, persistence.getPlayerLimit("shop", 0, alice))
    }

    @Test
    fun `a shop id that prefixes another's does not reset the other's players`() {
        stock.buy("shop", 0, 5, limited, alice)
        stock.buy("shop:vip", 0, 5, limited, alice)

        stock.resetPlayers("shop")

        assertEquals(10, stock.getStock("shop", 0, 10, alice))
        assertEquals(5, stock.getStock("shop:vip", 0, 10, alice))
    }

    @Test
    fun `an unlimited stock tracks nothing for any player`() {
        stock.buy("shop", 0, 50, PriceStrategy(stockMax = 0), alice)

        assertEquals(0, artifact.load().items.size)
    }

    @Test
    fun `the shared key and a player's key never collide`() {
        val shared = StockKeys.of("shop", 0, null)
        val own = StockKeys.of("shop", 0, alice)

        assertFalse(StockKeys.isPlayerStockOf("shop", shared))
        assertTrue(StockKeys.isPlayerStockOf("shop", own))
    }
}
