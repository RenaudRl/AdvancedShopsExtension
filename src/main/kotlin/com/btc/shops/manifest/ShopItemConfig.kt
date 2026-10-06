package com.btc.shops.manifest

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.utils.item.Item

/**
 * Configuration for a single item in a shop.
 */
@Serializable
data class ShopItemConfig(
    @Help("Item displayed for this shop entry.")
    val item: Var<Item> = ConstVar(Item.Empty),
    @Help("Custom display name for the item.")
    val name: String = "",
    @Help("Additional lore lines for this item.")
    val lore: List<String> = emptyList(),
    @Help("Criteria required for this item to be displayed.")
    val criteria: List<@Contextual Criteria> = emptyList(),

    @Help("Whether players can buy this item.")
    val buyEnabled: Boolean = true,
    @Help("Whether players can sell this item.")
    val sellEnabled: Boolean = true,

    @Help("How the price is determined: FIXED, DYNAMIC or ITEMS.")
    val priceMode: PriceMode = PriceMode.DYNAMIC,

    @Help(
        "Price paid in items for THIS article. When set it replaces the shop's defaultItemPrice; " +
            "when empty the shop provides its own. Only used when priceMode is ITEMS."
    )
    val itemPrice: List<ItemCost> = emptyList(),

    @Help("Fixed buy price per unit. Only used when priceMode is FIXED.")
    val fixedBuyPrice: Double = 0.0,
    @Help("Fixed sell price per unit. Only used when priceMode is FIXED.")
    val fixedSellPrice: Double = 0.0,

    @Help("Dynamic pricing strategy. Only used when priceMode is DYNAMIC.")
    val dynamicPricing: PriceStrategy = PriceStrategy(),

    @Help("Action triggered by the primary click (default: left click).")
    val primaryAction: ShopClickAction = ShopClickAction.BUY_1,
    @Help("Action triggered by the secondary click (default: right click).")
    val secondaryAction: ShopClickAction = ShopClickAction.SELL_1,
    @Help("Action triggered by shift-click.")
    val shiftAction: ShopClickAction = ShopClickAction.BUY_SUBMENU,

    @Help("Maximum amount a single player can purchase per reset cycle. 0 = unlimited.")
    val playerLimit: Int = 0,
    @Help("Maximum amount that can be purchased globally (all players combined) per reset cycle. 0 = unlimited.")
    val globalLimit: Int = 0,

    @Help("How often, in ticks, the shop checks whether the stock of this item has changed while it is open; the shop is redrawn only when it has. Only used when the item has a finite stock. 0 = the shop's defaultRefreshTicks.")
    val refreshTicks: Int = 0,

    @Help("Fixed slot for this item, counted row by row from 0 inside the shop's content area (9 columns wide, or contentColumns in tab mode). The item stays there on every page and leaves the SHOP_ITEM markers to the others. Keep the slot free of any other layout item. -1, or a slot outside the area = placed on the next free SHOP_ITEM marker.")
    val guiSlot: Int = -1,

    @Help("Whether buy max is enabled for this item.")
    val buyMaxEnabled: Boolean = true,
    @Help("Whether the sell-all action is enabled for this item. When off, sell all is refused with the shop's cannotSellMessage; selling a chosen amount is unaffected.")
    val sellAllEnabled: Boolean = true,

    @Help("Per-item tax rate override (percentage). Set to -1 to use the shop's global taxRate. Only effective when shop taxMode is PER_ITEM.")
    val taxRateOverride: Double = -1.0,
)
