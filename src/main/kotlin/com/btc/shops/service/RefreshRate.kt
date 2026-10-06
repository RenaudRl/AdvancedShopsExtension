package com.btc.shops.service

/**
 * How often the stock shown by an open shop is redrawn.
 *
 * Only an article with a finite stock ever changes under the player's eyes (another player buys,
 * a reset refills it), so only those are refreshed: a shop made of unlimited articles costs no
 * periodic work at all.
 */
object RefreshRate {

    /** Duration of one server tick, the unit of every refresh field. Protocol constant. */
    const val TICK_MILLIS = 50L

    /**
     * Ticks between two redraws of one article: its own `refreshTicks`, else the shop's default.
     * Zero means never.
     */
    fun effectiveTicks(itemTicks: Int, shopDefaultTicks: Int): Int =
        if (itemTicks > 0) itemTicks else shopDefaultTicks.coerceAtLeast(0)

    /**
     * Ticks between two redraws of the whole menu: the fastest of the refreshed articles, or null
     * when none is refreshed. Slower articles are held back by [isDue].
     */
    fun menuPeriodTicks(effectiveTicks: List<Int>): Long? =
        effectiveTicks.filter { it > 0 }.minOrNull()?.toLong()

    /**
     * Whether an article last drawn at [lastDrawnMillis] must be rebuilt at [nowMillis].
     * A menu redraw happens at the fastest article's pace; this keeps a slower one at its own.
     * One tick of slack: the scheduler fires a hair early as often as late, and an article whose
     * period equals the menu's must not skip every other redraw because of it.
     */
    fun isDue(effectiveTicks: Int, lastDrawnMillis: Long, nowMillis: Long): Boolean =
        nowMillis - lastDrawnMillis + TICK_MILLIS >= effectiveTicks * TICK_MILLIS
}
