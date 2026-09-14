package com.btc.shops.manifest

/**
 * Determines how item prices are calculated.
 */
enum class PriceMode {
    /** Prices never change, regardless of stock. */
    FIXED,
    /** Prices fluctuate with stock levels using [PriceStrategy]. */
    DYNAMIC,

    /**
     * The price is paid in **items**, not in currency: see [ItemCost].
     *
     * The article uses its own `itemPrice` list when set, otherwise the shop's `defaultItemPrice`.
     * Neither may be empty: a price declared in items and left empty is not "free", it is a
     * configuration mistake — the sale is refused and logged.
     */
    ITEMS
}
