package com.btc.shops.service

import com.btc.shops.ShopTextTemplate

/**
 * The words around a price paid in items: the lore lines and the list of what a player lacks.
 * The wordings are fields of the shop definition; this holds only the assembly, free of the server so it can be tested.
 */
internal object ItemPriceText {

    /** One price line, `{amount}` and `{name}` filled. */
    fun line(format: String, amount: Int, name: String): String =
        ShopTextTemplate.fill(format, mapOf("amount" to "$amount", "name" to name))

    /** The `{item_price}` lore token: one [line] per entry of [lines] (amount, name), separated by a line break. */
    fun describe(lines: List<Pair<Int, String>>, format: String): String =
        lines.joinToString("\n") { (amount, name) -> line(format, amount, name) }

    /** The `{missing}` list of a refusal: [missing] maps an item name to the quantity that is lacking. */
    fun missing(missing: Map<String, Int>, format: String, separator: String): String =
        missing.entries.joinToString(separator) { (name, amount) -> line(format, amount, name) }
}
