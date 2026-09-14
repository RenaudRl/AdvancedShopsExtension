package com.btc.shops.service

/**
 * The rules of a price paid in items, isolated from Bukkit and Typewriter.
 *
 * They live here rather than inline in the transaction handler because they carry the three
 * decisions where a mistake would go unnoticed:
 *
 * 1. **which list applies** — the article's own, otherwise the shop's;
 * 2. **an empty price is not free** — it is a configuration mistake, hence a refusal;
 * 3. **what is missing is computed before anything is removed**, never during.
 *
 * Production and tests go through the same functions: no mirror.
 */
object ItemPriceRule {

    /** What a price in items can be, once the two lists are confronted. */
    sealed interface Resolution<out T> {
        /** The price that applies, never empty. */
        data class Price<T>(val lines: List<T>) : Resolution<T>

        /**
         * No list is set, neither on the article nor on the shop.
         *
         * A **configuration mistake**, not a free article: an ITEMS shop with no price would hand
         * out its stock. An empty list treated as "nothing to check" reads as full acceptance.
         */
        data object Absent : Resolution<Nothing>

        /** At least one line asks for a zero or negative quantity: the price means nothing. */
        data class InvalidQuantity(val position: Int, val quantity: Int) : Resolution<Nothing>
    }

    /**
     * The price list that really applies.
     *
     * [override] is the article's, [default] the shop's. The override wins as soon as it is set —
     * even when cheaper or dearer: it is a substitution, not an addition. Summing both would charge
     * twice an article that only meant to say something different from the shop.
     */
    fun <T> resolve(
        override: List<T>,
        default: List<T>,
        quantity: (T) -> Int,
    ): Resolution<T> {
        val lines = override.ifEmpty { default }
        if (lines.isEmpty()) return Resolution.Absent

        lines.forEachIndexed { i, line ->
            val q = quantity(line)
            if (q <= 0) return Resolution.InvalidQuantity(i, q)
        }
        return Resolution.Price(lines)
    }

    /**
     * What the player lacks, per item key, to pay for [multiplier] units.
     *
     * A map rather than a boolean is not decoration: the refusal must tell the player **what** is
     * missing and **how much**. A bare "no" on a four-ingredient price sends them guessing.
     *
     * An empty map means "payable". [multiplier] is the quantity bought: a price is expressed
     * **per unit**, so buying eight units costs eight times the price.
     */
    fun <K> missing(
        required: Map<K, Int>,
        available: Map<K, Int>,
        multiplier: Int,
    ): Map<K, Int> {
        if (multiplier <= 0) return emptyMap()
        return required.mapNotNull { (key, perUnit) ->
            val due = perUnit * multiplier
            val have = available[key] ?: 0
            if (have >= due) null else key to (due - have)
        }.toMap()
    }

    /**
     * The largest number of units payable with what the player holds.
     *
     * Backs "buy max". A price whose line asks for zero cannot be divided: [resolve] already
     * refused it, but the guard is repeated because this function is public and may be called
     * on its own.
     */
    fun <K> affordableUnits(required: Map<K, Int>, available: Map<K, Int>): Int {
        if (required.isEmpty()) return 0
        return required.minOf { (key, perUnit) ->
            if (perUnit <= 0) 0 else (available[key] ?: 0) / perUnit
        }
    }
}
