package com.btc.shops.service

import kotlin.test.Test
import kotlin.test.assertEquals

/** The wordings around a price paid in items, and the cooldown pattern: the defaults read as the shop always showed them. */
class ItemPriceTextTest {
    private val lineFormat = "- {amount} × {name}"
    private val missingFormat = "{amount} × {name}"

    @Test
    fun `the default lore lists one dash line per requested item`() {
        val text = ItemPriceText.describe(listOf(12 to "Copper Ingot", 3 to "Kelp"), lineFormat)

        assertEquals("- 12 × Copper Ingot\n- 3 × Kelp", text)
    }

    @Test
    fun `no price line gives an empty lore text`() {
        assertEquals("", ItemPriceText.describe(emptyList(), lineFormat))
    }

    @Test
    fun `the line format can be reworded`() {
        assertEquals("12x Copper", ItemPriceText.describe(listOf(12 to "Copper"), "{amount}x {name}"))
    }

    @Test
    fun `the default missing list reads 3 × Kelp, 12 × Copper Ingot`() {
        val missing = linkedMapOf("Kelp" to 3, "Copper Ingot" to 12)

        assertEquals("3 × Kelp, 12 × Copper Ingot", ItemPriceText.missing(missing, missingFormat, ", "))
    }

    @Test
    fun `the missing list takes the separator the shop sets`() {
        val missing = linkedMapOf("Kelp" to 3, "Iron" to 1)

        assertEquals("3 Kelp / 1 Iron", ItemPriceText.missing(missing, "{amount} {name}", " / "))
    }

    @Test
    fun `an item name that looks like a placeholder is printed as is`() {
        assertEquals("1 × {amount}", ItemPriceText.missing(mapOf("{amount}" to 1), missingFormat, ", "))
    }

    @Test
    fun `the default cooldown pattern counts days with a d`() {
        val millis = ((1 * 86_400) + (2 * 3_600) + (3 * 60) + 4) * 1000L

        assertEquals("1d:02h:03m:04s", formatDuration(millis, "{d}d:{h}h:{m}m:{s}s"))
    }

    @Test
    fun `a cooldown pattern from the placeholder replaces the shop one`() {
        assertEquals("02h03m",formatDuration((2 * 3_600 + 3 * 60) * 1000L, "{h}h{m}m"))
    }
}
