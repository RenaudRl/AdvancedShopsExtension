package com.btc.shops.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Guards the three decisions of a price paid in items: which list applies, that an empty price
 * refuses rather than gives away, and that what is missing is named before anything moves.
 */
class ItemPriceRuleTest {

    private data class Line(val key: String, val amount: Int)

    @Test
    fun `the article's own price replaces the shop's, it is never added to it`() {
        val own = listOf(Line("iron", 2))
        val shop = listOf(Line("gold", 5), Line("iron", 9))

        val resolution = ItemPriceRule.resolve(own, shop, Line::amount)

        assertEquals(ItemPriceRule.Resolution.Price(own), resolution)
    }

    @Test
    fun `an article without its own price falls back to the shop's`() {
        val shop = listOf(Line("gold", 5))

        assertEquals(ItemPriceRule.Resolution.Price(shop), ItemPriceRule.resolve(emptyList(), shop, Line::amount))
    }

    @Test
    fun `no price anywhere is a configuration mistake, not a free article`() {
        assertIs<ItemPriceRule.Resolution.Absent>(ItemPriceRule.resolve(emptyList<Line>(), emptyList(), Line::amount))
    }

    @Test
    fun `a line asking for zero or less invalidates the whole price`() {
        val lines = listOf(Line("gold", 3), Line("iron", 0))

        assertEquals(
            ItemPriceRule.Resolution.InvalidQuantity(position = 1, quantity = 0),
            ItemPriceRule.resolve(lines, emptyList(), Line::amount),
        )
    }

    @Test
    fun `missing is computed per unit bought and names only what is lacking`() {
        val required = mapOf("gold" to 2, "iron" to 1)
        val available = mapOf("gold" to 5, "iron" to 10)

        assertEquals(emptyMap(), ItemPriceRule.missing(required, available, multiplier = 2))
        assertEquals(mapOf("gold" to 1), ItemPriceRule.missing(required, available, multiplier = 3))
        assertEquals(emptyMap(), ItemPriceRule.missing(required, available, multiplier = 0))
    }

    @Test
    fun `affordable units is bounded by the scarcest ingredient`() {
        val required = mapOf("gold" to 2, "iron" to 1)

        assertEquals(2, ItemPriceRule.affordableUnits(required, mapOf("gold" to 5, "iron" to 10)))
        assertEquals(0, ItemPriceRule.affordableUnits(required, mapOf("gold" to 5)))
        assertEquals(0, ItemPriceRule.affordableUnits(emptyMap<String, Int>(), mapOf("gold" to 5)))
    }
}
