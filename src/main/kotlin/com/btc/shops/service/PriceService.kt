package com.btc.shops.service

import com.btc.shops.manifest.PriceMode
import com.btc.shops.manifest.PriceStrategy
import com.btc.shops.manifest.ShopItemConfig
import com.typewritermc.core.extension.annotations.Singleton

/**
 * Calculates buy and sell prices for both FIXED and DYNAMIC modes.
 */
@Singleton
class PriceService {
    fun calculateBuyPrice(stock: Int, strategy: PriceStrategy): Double {
        val clamped = stock.coerceIn(0, strategy.stockMax)
        return strategy.calculateBuyPrice(clamped)
    }

    fun calculateSellPrice(stock: Int, strategy: PriceStrategy): Double {
        val clamped = stock.coerceIn(0, strategy.stockMax)
        return strategy.calculateSellPrice(clamped)
    }

    /** Get the effective buy price for an item based on its price mode. */
    fun getEffectiveBuyPrice(stock: Int, cfg: ShopItemConfig): Double {
        return when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedBuyPrice
            PriceMode.DYNAMIC -> calculateBuyPrice(stock, cfg.dynamicPricing)
        }
    }

    /** Get the effective sell price for an item based on its price mode. */
    fun getEffectiveSellPrice(stock: Int, cfg: ShopItemConfig): Double {
        return when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedSellPrice
            PriceMode.DYNAMIC -> calculateSellPrice(stock, cfg.dynamicPricing)
        }
    }
}
