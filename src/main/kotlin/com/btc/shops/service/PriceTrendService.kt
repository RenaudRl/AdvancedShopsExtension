package com.btc.shops.service

import com.btc.shops.manifest.ShopArtifact
import com.btc.shops.manifest.ShopDefinitionEntry
import com.typewritermc.core.extension.annotations.Singleton

/**
 * The icon for a trend as [PriceTrendService.getTrend] words it: "up" and "down" have their own, anything else
 * ("stable", or "unknown" while no earlier price is recorded) takes the stable one.
 */
internal fun trendIcon(trend: String, up: String, down: String, stable: String): String = when (trend) {
    "up" -> up
    "down" -> down
    else -> stable
}

/**
 * Tracks price trends for shop items.
 * Compares current price with previous price to determine direction.
 */
@Singleton
class PriceTrendService(
    private val artifactPersistence: ShopArtifactPersistenceService
) {

    fun getTrend(shopId: String, itemIndex: Int): String {
        val previousPrice = getPreviousPrice(shopId, itemIndex)
        val currentPrice = getCurrentPrice(shopId, itemIndex)
        if (previousPrice < 0) return "unknown"
        return when {
            currentPrice > previousPrice -> "up"
            currentPrice < previousPrice -> "down"
            else -> "stable"
        }
    }

    /** The icon the shop's own fields give to the current trend of the item. */
    fun getTrendIcon(definition: ShopDefinitionEntry, itemIndex: Int): String =
        trendIcon(getTrend(definition.id, itemIndex), definition.trendUpIcon, definition.trendDownIcon, definition.trendStableIcon)

    fun getPriceDelta(shopId: String, itemIndex: Int): Double {
        val previousPrice = getPreviousPrice(shopId, itemIndex)
        val currentPrice = getCurrentPrice(shopId, itemIndex)
        if (previousPrice < 0) return 0.0
        return currentPrice - previousPrice
    }

    fun recordPrice(shopId: String, itemIndex: Int, price: Double) {
        artifactPersistence.update { data ->
            val key = "prevprice:$shopId:$itemIndex"
            val state = data.items[key] ?: ShopArtifact.ShopItemState()
            data.items[key] = state.copy(priceState = price, lastUpdate = System.currentTimeMillis())
        }
    }

    private fun getPreviousPrice(shopId: String, itemIndex: Int): Double {
        return try {
            val data = artifactPersistence.load()
            data.items["prevprice:$shopId:$itemIndex"]?.priceState ?: -1.0
        } catch (_: Exception) { -1.0 }
    }

    private fun getCurrentPrice(shopId: String, itemIndex: Int): Double {
        return try {
            val data = artifactPersistence.load()
            data.items["stock:$shopId:$itemIndex"]?.priceState ?: 0.0
        } catch (_: Exception) { 0.0 }
    }
}
