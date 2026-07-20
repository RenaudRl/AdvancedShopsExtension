package com.btc.shops.manifest

/**
 * Determines how item prices are calculated.
 */
enum class PriceMode {
    /** Prices never change, regardless of stock. */
    FIXED,
    /** Prices fluctuate with stock levels using [PriceStrategy]. */
    DYNAMIC
}
