package com.btc.shops.service

import com.btc.shops.manifest.PriceStrategy
import com.typewritermc.core.extension.annotations.Singleton
import java.util.UUID

/**
 * Handles stock mutations for shop items.
 * All persistence is delegated to [ShopPersistenceService].
 * Supports both shared and per-player stock tracking.
 */
@Singleton
class StockService(
    private val persistence: ShopPersistenceService
) {
    fun getStock(shopId: String, itemId: Int, max: Int, playerUuid: UUID? = null): Int =
        persistence.getStock(shopId, itemId, max, playerUuid)

    /** Deduct [amount] from [current] stock. */
    fun buy(current: Int, amount: Int): Int {
        require(amount >= 0) { "Amount must be positive" }
        if (current < amount) throw IllegalArgumentException("Insufficient stock: $current < $amount")
        return current - amount
    }

    fun buy(shopId: String, itemId: Int, amount: Int, strategy: PriceStrategy, playerUuid: UUID? = null): Int {
        // stockMax <= 0 means unlimited supply (same "0 = unlimited" convention as the price
        // bounds and limits). Fixed-price items default to stockMax = 0, so enforcing stock here
        // made every purchase throw "Insufficient stock: 0 < 1". Nothing to track when unlimited.
        if (strategy.stockMax <= 0) return Int.MAX_VALUE
        val current = getStock(shopId, itemId, strategy.stockMax, playerUuid)
        val updated = buy(current, amount)
        persistence.setStock(shopId, itemId, updated, playerUuid)
        return updated
    }

    /** Adds [amount] to [current] stock but not beyond [max]. */
    fun sell(current: Int, amount: Int, max: Int): Int {
        require(amount >= 0) { "Amount must be positive" }
        val result = current + amount
        return if (result > max) max else result
    }

    fun sell(shopId: String, itemId: Int, amount: Int, strategy: PriceStrategy, playerUuid: UUID? = null): Int {
        // Unlimited stock: selling into an infinite pool tracks nothing (and sell(current, amount,
        // max=0) would clamp the stored stock to 0, corrupting a later switch to limited stock).
        if (strategy.stockMax <= 0) return Int.MAX_VALUE
        val current = getStock(shopId, itemId, strategy.stockMax, playerUuid)
        val updated = sell(current, amount, strategy.stockMax)
        persistence.setStock(shopId, itemId, updated, playerUuid)
        return updated
    }

    /** Resets stock back to its maximum value. */
    fun reset(max: Int): Int = max

    fun reset(shopId: String, itemId: Int, max: Int, playerUuid: UUID? = null) {
        persistence.resetStock(shopId, itemId, max, playerUuid)
    }
}
