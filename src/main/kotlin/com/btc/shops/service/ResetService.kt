package com.btc.shops.service

import com.btc.shops.manifest.ResetPolicy
import com.btc.shops.manifest.ShopDefinitionEntry
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.entries.Query
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * Service responsible for calculating reset times and performing automatic stock resets.
 * Folia-safe: uses globalRegionScheduler for periodic checks.
 */
@Singleton
class ResetService(
    private val plugin: JavaPlugin,
    private val stockService: StockService,
    private val playerLimitService: PlayerLimitService,
    private val globalLimitService: GlobalLimitService
) {
    private val nextReset = ConcurrentHashMap<String, Long>()

    init {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, Consumer { checkGlobalResets() }, 100L, 1200L)
    }

    private fun checkGlobalResets() {
        val now = System.currentTimeMillis()
        // Use the non-inline instance query, NOT the inline reified `Query.find<T>()`. The inline
        // form expands into ResetService and emits a synthetic `$$inlined$find$1` class that
        // Typewriter's extension ClassLoader cannot resolve when this scheduler task runs
        // (NoClassDefFoundError every tick). `Query(klass).find()` is a plain constructor + method
        // call whose lambdas live in the engine jar, so nothing synthetic is generated here.
        Query(ShopDefinitionEntry::class).find().forEach { definition ->
            if (shouldReset(definition, now)) {
                performReset(definition)
            }
        }
    }

    fun shouldReset(definition: ShopDefinitionEntry, now: Long = System.currentTimeMillis()): Boolean {
        val next = nextReset.getOrPut(definition.id) { calculateNext(now, definition.reset) }
        return if (now >= next) {
            nextReset[definition.id] = calculateNext(now, definition.reset)
            true
        } else {
            false
        }
    }

    private fun performReset(definition: ShopDefinitionEntry) {
        // Reset stocks (shared)
        definition.items.forEachIndexed { index, cfg ->
            stockService.reset(definition.id, index, cfg.dynamicPricing.stockMax)
        }

        // Reset per-player stocks if perPlayer mode
        if (definition.perPlayer) {
            // Per-player stocks are reset by clearing the persistence keys
            // The getStock() will return max for any key not found
            playerLimitService.reset(definition)
        }

        // Reset player limits
        playerLimitService.reset(definition)

        // Reset global limits (per-item)
        globalLimitService.reset(definition)

        // Reset global limits (per-type: buy/sell)
        globalLimitService.resetGlobal(definition, "buy")
        globalLimitService.resetGlobal(definition, "sell")
    }

    /** Applies the same full stock/limit reset as the automatic policy. */
    fun resetNow(definition: ShopDefinitionEntry) {
        performReset(definition)
        nextReset[definition.id] = calculateNext(System.currentTimeMillis(), definition.reset)
    }

    fun remaining(definition: ShopDefinitionEntry): Long {
        val now = System.currentTimeMillis()
        val next = nextReset.getOrPut(definition.id) { calculateNext(now, definition.reset) }
        return (next - now).coerceAtLeast(0)
    }

    private fun calculateNext(now: Long, policy: ResetPolicy): Long {
        val zone = ZoneId.systemDefault()
        val zoned = Instant.ofEpochMilli(now).atZone(zone)

        return when (policy) {
            is ResetPolicy.None -> Long.MAX_VALUE
            is ResetPolicy.Interval -> now + TimeUnit.SECONDS.toMillis(policy.seconds)
            is ResetPolicy.Daily -> {
                val base = zoned.withHour(policy.hour.coerceIn(0, 23))
                    .withMinute(policy.minute.coerceIn(0, 59)).withSecond(0).withNano(0)
                val next = if (base.toInstant().toEpochMilli() > now) base else base.plusDays(1)
                next.toInstant().toEpochMilli()
            }
            is ResetPolicy.Weekly -> {
                // Clamp every component: the web editor serializes an un-set number as 0, and
                // DayOfWeek.of(0) throws (valid range is 1..7) — which crashed this task on every
                // tick, aborting shouldReset() and with it the shop opening.
                val base = zoned.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(policy.day.coerceIn(1, 7))))
                    .withHour(policy.hour.coerceIn(0, 23))
                    .withMinute(policy.minute.coerceIn(0, 59)).withSecond(0).withNano(0)
                val next = if (base.toInstant().toEpochMilli() > now) base else base.plusWeeks(1)
                next.toInstant().toEpochMilli()
            }
            is ResetPolicy.Monthly -> {
                val day = policy.day
                val base = zoned.withDayOfMonth(day.coerceIn(1, zoned.toLocalDate().lengthOfMonth()))
                    .withHour(policy.hour.coerceIn(0, 23))
                    .withMinute(policy.minute.coerceIn(0, 59)).withSecond(0).withNano(0)
                val next = if (base.toInstant().toEpochMilli() > now) base else base.plusMonths(1)
                    .withDayOfMonth(day.coerceIn(1, base.plusMonths(1).toLocalDate().lengthOfMonth()))
                next.toInstant().toEpochMilli()
            }
            is ResetPolicy.Cron -> {
                com.btc.shops.util.CronUtil.next(policy.expression, now)
            }
        }
    }
}
