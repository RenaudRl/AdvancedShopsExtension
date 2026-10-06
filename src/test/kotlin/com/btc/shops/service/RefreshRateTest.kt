package com.btc.shops.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Guards how often a shop redraws the stock it shows. */
class RefreshRateTest {

    @Test
    fun `an item's own period replaces the shop's default`() {
        assertEquals(40, RefreshRate.effectiveTicks(itemTicks = 40, shopDefaultTicks = 20))
    }

    @Test
    fun `an item without a period follows the shop's default`() {
        assertEquals(20, RefreshRate.effectiveTicks(itemTicks = 0, shopDefaultTicks = 20))
    }

    @Test
    fun `a negative default means never, not a negative period`() {
        assertEquals(0, RefreshRate.effectiveTicks(itemTicks = 0, shopDefaultTicks = -5))
    }

    @Test
    fun `the menu redraws at the pace of its fastest refreshed item`() {
        assertEquals(10L, RefreshRate.menuPeriodTicks(listOf(40, 10, 20)))
    }

    @Test
    fun `items that are never refreshed do not make the menu redraw`() {
        assertEquals(20L, RefreshRate.menuPeriodTicks(listOf(0, 20)))
        assertNull(RefreshRate.menuPeriodTicks(listOf(0, 0)))
        assertNull(RefreshRate.menuPeriodTicks(emptyList()))
    }

    @Test
    fun `a slow item is held back until its own period has run`() {
        val period = 100 // ticks = 5 s
        assertFalse(RefreshRate.isDue(period, lastDrawnMillis = 0, nowMillis = 1_000))
        assertTrue(RefreshRate.isDue(period, lastDrawnMillis = 0, nowMillis = 5_000))
    }

    @Test
    fun `an item at the menu's own pace is not skipped when the scheduler fires a tick early`() {
        val period = 20 // ticks = 1 s
        assertTrue(RefreshRate.isDue(period, lastDrawnMillis = 0, nowMillis = 950))
    }
}
