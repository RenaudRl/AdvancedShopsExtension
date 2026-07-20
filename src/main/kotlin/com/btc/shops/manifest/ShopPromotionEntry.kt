package com.btc.shops.manifest

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.ManifestEntry
import kotlinx.serialization.Contextual

/**
 * A temporary promotion that applies a discount to shop items.
 *
 * When active, all items in the target shop (or specific items if specified)
 * have their buy/sell prices reduced by [discountPercent].
 *
 * Optionally bound to criteria (e.g., only active during events) and
 * auto-expires after [durationSeconds] (0 = permanent until manually removed).
 */
@Tags("shop", "promotion")
@Entry("shop_promotion", "A shop discount promotion", Colors.YELLOW, "mdi:sale")
class ShopPromotionEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("The shop this promotion applies to.")
    val targetShop: Ref<ShopDefinitionEntry> = emptyRef(),
    @Help("Discount percentage (e.g., 20.0 = 20% off). Applied to both buy and sell prices.")
    val discountPercent: Double = 0.0,
    @Help("Specific item indices this promotion applies to. Empty = all items.")
    val targetItemIndices: List<Int> = emptyList(),
    @Help("Criteria that must be met for this promotion to be active.")
    val criteria: List<@Contextual Criteria> = emptyList(),
    @Help("Duration in seconds before this promotion auto-expires. 0 = permanent.")
    val durationSeconds: Long = 0,
    @Help("Timestamp when this promotion was created (auto-set).")
    val createdAt: Long = System.currentTimeMillis()
) : ManifestEntry {

    val isExpired: Boolean
        get() = durationSeconds > 0 && (System.currentTimeMillis() - createdAt) > durationSeconds * 1000

    val multiplier: Double
        get() = 1.0 - (discountPercent / 100.0).coerceIn(0.0, 1.0)
}
