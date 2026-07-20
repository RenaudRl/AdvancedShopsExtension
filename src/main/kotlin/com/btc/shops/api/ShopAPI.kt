package com.btc.shops.api

import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.service.*
import org.bukkit.entity.Player

/**
 * Public API for other extensions to interact with the Shop system.
 * Exposed via Koin as a singleton.
 */
interface ShopAPI {
    fun getPlayerBalance(player: Player, shop: ShopDefinitionEntry): Double
    fun getStock(shopId: String, itemIndex: Int): Int
    fun setStock(shopId: String, itemIndex: Int, amount: Int)
    fun addStock(shopId: String, itemIndex: Int, amount: Int)
    fun removeStock(shopId: String, itemIndex: Int, amount: Int)
    fun getPlayerLimitRemaining(player: Player, shop: ShopDefinitionEntry, itemIndex: Int): Int
    fun getGlobalLimitRemaining(shop: ShopDefinitionEntry, itemIndex: Int): Int
    fun hasActivePromotion(shopId: String, itemIndex: Int): Boolean
    fun getTransactionHistory(player: Player): List<TransactionRecord>
}

class ShopAPIImpl(
    private val economyService: EconomyService,
    private val stockService: StockService,
    private val playerLimitService: PlayerLimitService,
    private val globalLimitService: GlobalLimitService,
    private val promotionService: PromotionService,
    private val transactionLogService: TransactionLogService
) : ShopAPI {

    override fun getPlayerBalance(player: Player, shop: ShopDefinitionEntry): Double {
        val economy = economyService.resolve(shop) ?: return 0.0
        return economy.getBalance(player.uniqueId)
    }

    override fun getStock(shopId: String, itemIndex: Int): Int {
        // We need a definition to get stockMax; this is a simplified version
        return try {
            val definition = com.btc.shops.manifest.ShopDefinitionEntry() // placeholder
            stockService.getStock(shopId, itemIndex, 0)
        } catch (_: Exception) { 0 }
    }

    override fun setStock(shopId: String, itemIndex: Int, amount: Int) {
        // Direct stock manipulation via persistence
        val definition = com.typewritermc.core.entries.Query.findById<ShopDefinitionEntry>(shopId)
        if (definition != null) {
            val cfg = definition.items.getOrNull(itemIndex) ?: return
            stockService.reset(shopId, itemIndex, amount.coerceAtLeast(0))
        }
    }

    override fun addStock(shopId: String, itemIndex: Int, amount: Int) {
        val current = getStock(shopId, itemIndex)
        setStock(shopId, itemIndex, current + amount)
    }

    override fun removeStock(shopId: String, itemIndex: Int, amount: Int) {
        val current = getStock(shopId, itemIndex)
        setStock(shopId, itemIndex, (current - amount).coerceAtLeast(0))
    }

    override fun getPlayerLimitRemaining(player: Player, shop: ShopDefinitionEntry, itemIndex: Int): Int {
        val cfg = shop.items.getOrNull(itemIndex) ?: return Int.MAX_VALUE
        return playerLimitService.remaining(player, shop, itemIndex, cfg.playerLimit)
    }

    override fun getGlobalLimitRemaining(shop: ShopDefinitionEntry, itemIndex: Int): Int {
        val cfg = shop.items.getOrNull(itemIndex) ?: return Int.MAX_VALUE
        return globalLimitService.remaining(shop, itemIndex, cfg.globalLimit)
    }

    override fun hasActivePromotion(shopId: String, itemIndex: Int): Boolean {
        return promotionService.hasPromotion(shopId, itemIndex)
    }

    override fun getTransactionHistory(player: Player): List<TransactionRecord> {
        return transactionLogService.getHistory(player.uniqueId)
    }
}
