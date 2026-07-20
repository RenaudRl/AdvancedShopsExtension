package com.btc.shops.service

import com.btc.shops.manifest.PoolItem
import com.btc.shops.manifest.PriceMode
import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.manifest.ShopItemConfig
import com.btc.shops.manifest.ShopPoolEntry
import com.typewritermc.core.extension.annotations.Singleton
import kotlin.random.Random

/**
 * Resolves item pools referenced by shop definitions into concrete shop items.
 */
@Singleton
class ShopPoolService {

    /**
     * Resolves all pools referenced by a shop definition and returns the effective
     * list of shop items (original items + pool items).
     */
    fun resolveItems(definition: ShopDefinitionEntry): List<ShopItemConfig> {
        val directItems = definition.items.toMutableList()

        definition.itemPools.forEach { poolRef ->
            val pool = poolRef.get() ?: return@forEach
            val poolItems = resolvePool(pool)
            directItems.addAll(poolItems)
        }

        return directItems
    }

    /**
     * Converts pool entries into shop item configs.
     * Respects displayCount (0 = all items from pool).
     */
    private fun resolvePool(pool: ShopPoolEntry): List<ShopItemConfig> {
        val items = if (pool.displayCount > 0 && pool.displayCount < pool.items.size) {
            selectWeighted(pool.items, pool.displayCount)
        } else {
            pool.items
        }

        return items.map { poolItem ->
            ShopItemConfig(
                item = poolItem.item,
                name = poolItem.name,
                lore = buildPoolLore(poolItem),
                buyEnabled = true,
                sellEnabled = poolItem.isLimited,
                priceMode = PriceMode.FIXED,
                fixedBuyPrice = 0.0,
                fixedSellPrice = 0.0,
                playerLimit = if (poolItem.isLimited) poolItem.limit else 0,
                globalLimit = if (poolItem.isLimited) poolItem.limit else 0
            )
        }
    }

    private fun buildPoolLore(item: PoolItem): List<String> {
        val lore = item.lore.toMutableList()
        if (item.isLimited) {
            lore.add("<yellow>Limited: <red>${item.limit}")
        } else {
            lore.add("<yellow>Unlimited")
        }
        return lore
    }

    private fun selectWeighted(items: List<PoolItem>, count: Int): List<PoolItem> {
        if (items.isEmpty()) return emptyList()
        val totalWeight = items.sumOf { it.weight }
        if (totalWeight <= 0) return items.take(count)

        val selected = mutableListOf<PoolItem>()
        val available = items.toMutableList()
        var remainingWeight = totalWeight

        repeat(count.coerceAtMost(items.size)) {
            var roll = Random.nextDouble() * remainingWeight
            var cumulative = 0.0
            var chosenIndex = 0

            for ((i, item) in available.withIndex()) {
                cumulative += item.weight
                if (roll <= cumulative) {
                    chosenIndex = i
                    break
                }
            }

            val chosen = available.removeAt(chosenIndex)
            selected.add(chosen)
            remainingWeight -= chosen.weight
        }

        return selected
    }
}
