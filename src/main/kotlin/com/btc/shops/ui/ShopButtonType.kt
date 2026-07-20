package com.btc.shops.ui

/**
 * Button types for Shop layout pool placeholders.
 *
 * Declarative slots in the layout pool use "shop_button:" prefix
 * (e.g., "shop_button:BUY_1") to mark dynamic button positions.
 *
 * Main shop button types:
 * - SHOP_ITEM: dynamic placeholder replaced by the actual shop item
 * - BUY_1, SELL_1: buy/sell one unit
 * - BUY_STACK, SELL_STACK: buy/sell a full stack
 * - BUY_MAX, SELL_ALL: buy max possible / sell all matching
 * - BUY_SUBMENU, SELL_SUBMENU: open buy/sell sub-menu
 * - NEXT_PAGE, PREV_PAGE, INFO, CLOSE: navigation
 *
 * Sub-menu button types (use TYPE:param for amounts, e.g. "BUY_1:10"):
 * - BUY_1:N, SELL_1:N: buy/sell N units
 * - BUY_STACK, SELL_STACK: buy/sell full stack
 * - BUY_MAX, SELL_ALL: buy max / sell all
 * - BUY_CUSTOM, SELL_CUSTOM: open Paper Dialog for custom amount input
 * - BACK: return to main shop
 * - CLOSE: close the menu
 */
enum class ShopButtonType {
    /** Dynamic placeholder replaced by the actual shop item */
    SHOP_ITEM,
    /** Buy 1 unit */
    BUY_1,
    /** Sell 1 unit */
    SELL_1,
    /** Buy a full stack at once */
    BUY_STACK,
    /** Sell a full stack at once */
    SELL_STACK,
    /** Buy the maximum possible (limited by money + inventory + stock) */
    BUY_MAX,
    /** Sell all matching items from the player's inventory */
    SELL_ALL,
    /** Open the buy sub-menu for more options */
    BUY_SUBMENU,
    /** Open the sell sub-menu for more options */
    SELL_SUBMENU,
    /** Open the Paper Dialog for a custom buy amount */
    BUY_CUSTOM,
    /** Open the Paper Dialog for a custom sell amount */
    SELL_CUSTOM,
    /** Next page */
    NEXT_PAGE,
    /** Previous page */
    PREV_PAGE,
    /** Info button */
    INFO,
    /** Close button */
    CLOSE,
    /** Back to main shop from sub-menu */
    BACK,
}
