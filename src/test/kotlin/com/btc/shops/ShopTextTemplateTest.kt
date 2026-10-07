package com.btc.shops

import kotlin.test.Test
import kotlin.test.assertEquals

class ShopTextTemplateTest {

    @Test
    fun `every placeholder is replaced, also when it appears twice`() {
        val filled = ShopTextTemplate.fill("{shop}[{index}] then {shop}", mapOf("shop" to "mine", "index" to "3"))

        assertEquals("mine[3] then mine", filled)
    }

    @Test
    fun `a placeholder without a value stays as written so a later step can fill it`() {
        assertEquals("Buy: {buy} in mine", ShopTextTemplate.fill("Buy: {buy} in {shop}", mapOf("shop" to "mine")))
    }

    @Test
    fun `a value that looks like a placeholder is not filled a second time`() {
        val filled = ShopTextTemplate.fill("{name} owns {id}", mapOf("name" to "{id}", "id" to "mine"))

        assertEquals("{id} owns mine", filled)
    }

    @Test
    fun `MiniMessage tags of the template are kept as written`() {
        val text = "<gold>Loaded shops ({count}):"

        assertEquals("<gold>Loaded shops (4):", ShopTextTemplate.fill(text, mapOf("count" to "4")))
    }

    @Test
    fun `the default stock reply reads as the command always printed it`() {
        val filled = ShopTextTemplate.fill("<green>{shop}[{index}] stock set to {stock}/{max}.", mapOf(
            "shop" to "mine", "index" to "2", "stock" to "5", "max" to "64",
        ))

        assertEquals("<green>mine[2] stock set to 5/64.", filled)
    }
}
