package com.btc.shops.service

/**
 * Tells an open shop when the stock it shows has really changed, so it is redrawn only then.
 *
 * Each watched item has its own period (see [RefreshRate]); at every [poll] only the items whose
 * period has run are looked at, and a change of their stock since the last look is reported.
 * A shop whose stocks do not move is never redrawn, however many players look at it.
 */
class StockWatch(items: List<Item>, startMillis: Long) {

    /** One watched item: [key] identifies it, [stock] is what the menu shows now. */
    data class Item(val key: Int, val periodTicks: Int, val stock: Int)

    private class State(val periodTicks: Int, var stock: Int, var checkedAt: Long)

    private val states = items
        .filter { it.periodTicks > 0 }
        .associate { it.key to State(it.periodTicks, it.stock, startMillis) }

    /** Whether any item is watched at all. */
    val isEmpty: Boolean get() = states.isEmpty()

    /** Ticks between two polls: the fastest watched item's period, null when nothing is watched. */
    val fastestPeriodTicks: Long? = RefreshRate.menuPeriodTicks(states.values.map { it.periodTicks })

    /** True when the stock of an item that is due has changed since it was last looked at. */
    fun poll(nowMillis: Long, stockOf: (Int) -> Int): Boolean {
        var changed = false
        for ((key, state) in states) {
            if (!RefreshRate.isDue(state.periodTicks, state.checkedAt, nowMillis)) continue
            state.checkedAt = nowMillis
            val current = stockOf(key)
            if (current != state.stock) {
                state.stock = current
                changed = true
            }
        }
        return changed
    }
}
