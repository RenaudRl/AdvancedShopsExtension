package com.btc.shops.manifest

import btcrenaud.gui.api.InteractionType
import com.typewritermc.core.extension.annotations.Help
import kotlinx.serialization.Serializable

/**
 * Maps every shop action to a configurable click type.
 *
 * Follows the exact same pattern as [btcrenaud.gui.entries.StorageInteractionConfig]
 * in the GUI Extension: each field maps a specific action to a mouse interaction type.
 *
 * All shop items share this configuration. Each item's [ShopItemConfig] determines
 * WHICH actions are available (via buyEnabled/sellEnabled/buyMaxEnabled/sellAllEnabled),
 * while this config determines WHICH click type triggers each action.
 *
 * The per-item [ShopItemConfig.primaryAction], [ShopItemConfig.secondaryAction],
 * and [ShopItemConfig.shiftAction] still define which of these actions appear
 * on the item's primary/secondary/shift interaction slots.
 */
@Serializable
data class ShopInteractionConfig(
    @Help("Click type to buy 1 unit of the selected item")
    val buy1Click: InteractionType = InteractionType.LEFT_CLICK,

    @Help("Click type to buy a full stack of the selected item")
    val buyStackClick: InteractionType = InteractionType.LEFT_CLICK,

    @Help("Click type to buy the maximum possible (limited by balance, inventory space, and stock)")
    val buyMaxClick: InteractionType = InteractionType.LEFT_CLICK,

    @Help("Click type to open the buy & sell sub-menu")
    val buySubmenuClick: InteractionType = InteractionType.SHIFT_LEFT_CLICK,

    @Help("Click type to open the custom buy amount dialog")
    val buyCustomClick: InteractionType = InteractionType.LEFT_CLICK,

    @Help("Click type to sell 1 unit of the selected item")
    val sell1Click: InteractionType = InteractionType.RIGHT_CLICK,

    @Help("Click type to sell a full stack of the selected item")
    val sellStackClick: InteractionType = InteractionType.LEFT_CLICK,

    @Help("Click type to sell all matching items from the player's inventory")
    val sellAllClick: InteractionType = InteractionType.LEFT_CLICK,

    @Help("Click type to open the sell sub-menu")
    val sellSubmenuClick: InteractionType = InteractionType.SHIFT_LEFT_CLICK,

    @Help("Click type to open the custom sell amount dialog")
    val sellCustomClick: InteractionType = InteractionType.LEFT_CLICK,

    @Help("Click type for navigation buttons (next page, previous page, close, info, back)")
    val navigationClick: InteractionType = InteractionType.LEFT_CLICK,
)
