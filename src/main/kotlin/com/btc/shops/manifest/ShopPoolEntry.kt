package com.btc.shops.manifest

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.utils.item.Item
import kotlinx.serialization.Serializable

/**
 * A pool of items that can be referenced by shop definitions.
 * Pools allow grouping items with weighted random selection and limited/unlimited quantities.
 */
@Tags("shop", "pool")
@Entry("item_pool", "A weighted pool of items for shops", Colors.CYAN, "mdi:package-variant-closed")
class ShopPoolEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Items in this pool with their weights and limits.")
    val items: List<PoolItem> = emptyList(),
    @Help("Number of items from this pool to display in the shop. 0 means all.")
    val displayCount: Int = 0
) : ManifestEntry

/**
 * A single item within a pool, with weight and limit configuration.
 */
@Serializable
data class PoolItem(
    @Help("The item to give to the player.")
    val item: Var<Item> = ConstVar(Item.Empty),
    @Help("Weight for random selection. Higher = more likely.")
    val weight: Double = 1.0,
    @Help("Quantity of this item given per purchase.")
    val quantity: Int = 1,
    @Help("If true, this item has a limited supply within the pool.")
    val isLimited: Boolean = false,
    @Help("Maximum available count for this item (only used when isLimited is true).")
    val limit: Int = 0,
    @Help("Custom display name override for this pool item.")
    val name: String = "",
    @Help("Custom lore override for this pool item.")
    val lore: List<String> = emptyList()
)
