package com.btc.shops.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guards that an open shop is redrawn only when a stock it shows has really changed. */
class StockWatchTest {

    private val stocks = mutableMapOf(0 to 10, 1 to 5)
    private var lookups = 0
    private val read: (Int) -> Int = { lookups++; stocks.getValue(it) }

    private fun watch(vararg periods: Int) =
        StockWatch(periods.mapIndexed { i, p -> StockWatch.Item(i, p, stocks.getValue(i)) }, startMillis = 0)

    @Test
    fun `a shop whose stocks do not move is never redrawn`() {
        val watch = watch(20, 20)

        assertFalse(watch.poll(1_000, read))
        assertFalse(watch.poll(2_000, read))
        assertFalse(watch.poll(3_000, read))
    }

    @Test
    fun `a stock that changed triggers one redraw, then silence`() {
        val watch = watch(20, 20)

        stocks[0] = 7

        assertTrue(watch.poll(1_000, read))
        assertFalse(watch.poll(2_000, read))
    }

    @Test
    fun `a slow item is not even looked at before its period has run`() {
        val watch = watch(100)
        stocks[0] = 3

        assertFalse(watch.poll(1_000, read))
        assertEquals(0, lookups)
        assertTrue(watch.poll(5_000, read))
    }

    @Test
    fun `an item that is never refreshed is not watched`() {
        val watch = watch(0, 0)
        stocks[0] = 1

        assertTrue(watch.isEmpty)
        assertFalse(watch.poll(60_000, read))
        assertEquals(0, lookups)
    }

    @Test
    fun `a stock that goes back to what was shown is not a change`() {
        val watch = watch(20)
        stocks[0] = 4
        assertTrue(watch.poll(1_000, read))

        stocks[0] = 4
        assertFalse(watch.poll(2_000, read))
    }
}
