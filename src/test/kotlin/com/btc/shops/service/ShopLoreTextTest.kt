package com.btc.shops.service

import kotlin.test.Test
import kotlin.test.assertEquals

/** The two texts a shop owner now sets in the entries: the trend icons of a shop and the supply lines of a pool. */
class ShopLoreTextTest {
    private val up = "<red>▲</red>"
    private val down = "<green>▼</green>"
    private val stable = "<gray>●</gray>"

    @Test
    fun `a rising or falling price gets its own icon`() {
        assertEquals(up, trendIcon("up", up, down, stable))
        assertEquals(down, trendIcon("down", up, down, stable))
    }

    @Test
    fun `a steady price and a price with no history both take the stable icon`() {
        assertEquals(stable, trendIcon("stable", up, down, stable))
        assertEquals(stable, trendIcon("unknown", up, down, stable))
    }

    @Test
    fun `the icons are whatever the shop writes`() {
        assertEquals("UP!", trendIcon("up", "UP!", "DOWN!", "="))
    }

    @Test
    fun `a limited pool item ends with the limited line, its limit filled`() {
        val lore = poolLore(listOf("<gray>Rare"), isLimited = true, limit = 5, limitedLore = "<yellow>Limited: <red>{limit}", unlimitedLore = "<yellow>Unlimited")

        assertEquals(listOf("<gray>Rare", "<yellow>Limited: <red>5"), lore)
    }

    @Test
    fun `an unlimited pool item ends with the unlimited line and ignores its limit`() {
        val lore = poolLore(emptyList(), isLimited = false, limit = 9, limitedLore = "<yellow>Limited: <red>{limit}", unlimitedLore = "<yellow>Unlimited")

        assertEquals(listOf("<yellow>Unlimited"), lore)
    }

    @Test
    fun `the pool lines can be reworded and translated by the owner`() {
        val lore = poolLore(listOf("a"), isLimited = true, limit = 2, limitedLore = "Reste {limit} / Left {limit}", unlimitedLore = "Illimité")

        assertEquals(listOf("a", "Reste 2 / Left 2"), lore)
    }
}
