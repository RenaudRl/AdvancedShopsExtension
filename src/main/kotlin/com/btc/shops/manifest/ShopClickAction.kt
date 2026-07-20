package com.btc.shops.manifest

/**
 * Actions triggered by clicking a shop item.
 * Each shop item can have up to 3 click actions (primary, secondary, shift).
 */
enum class ShopClickAction {
    /** Do nothing on click */
    NONE,
    /** Buy 1 unit */
    BUY_1,
    /** Sell 1 unit */
    SELL_1,
    /** Buy a full stack */
    BUY_STACK,
    /** Sell a full stack */
    SELL_STACK,
    /** Buy the maximum possible */
    BUY_MAX,
    /** Sell all matching items */
    SELL_ALL,
    /** Open buy sub-menu */
    BUY_SUBMENU,
    /** Open sell sub-menu */
    SELL_SUBMENU,
    /** Open custom buy amount dialog */
    BUY_CUSTOM,
    /** Open custom sell amount dialog */
    SELL_CUSTOM,
}
