package com.btc.shops.manifest

/**
 * How taxes are applied to transactions.
 */
enum class TaxMode {
    /** Tax percentage applied to every buy/sell transaction. */
    PER_TRANSACTION,
    /** Tax percentage applied per item with item-level overrides. */
    PER_ITEM
}
