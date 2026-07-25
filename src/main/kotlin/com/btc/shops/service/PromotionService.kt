package com.btc.shops.service

import com.btc.shops.manifest.PriceMode
import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.manifest.ShopItemConfig
import com.btc.shops.manifest.ShopPromotionEntry
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.Singleton

/**
 * Applies active promotions to shop prices.
 * Checks for active ShopPromotionEntry instances and applies discounts.
 */
@Singleton
class PromotionService {

    /**
     * Returns the effective price after applying any active promotion.
     * If multiple promotions stack, they are multiplied.
     */
    fun getPromotedPrice(basePrice: Double, shopId: String, itemIndex: Int): Double {
        val promotions = getActivePromotions(shopId)
        if (promotions.isEmpty()) return basePrice

        var multiplier = 1.0
        for (promo in promotions) {
            if (promo.targetItemIndices.isEmpty() || itemIndex in promo.targetItemIndices) {
                multiplier *= promo.multiplier
            }
        }
        return basePrice * multiplier
    }

    fun getActivePromotions(shopId: String): List<ShopPromotionEntry> {
        return Query(ShopPromotionEntry::class).find().filter { promo ->
            promo.targetShop.get()?.id == shopId && !promo.isExpired
        }.toList()
    }

    fun hasPromotion(shopId: String, itemIndex: Int): Boolean {
        return getActivePromotions(shopId).any { promo ->
            promo.targetItemIndices.isEmpty() || itemIndex in promo.targetItemIndices
        }
    }
}
