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

/**
 * Service responsible for calculating reset times and performing automatic stock resets.
 */
@Singleton
class ResetService(
    private val plugin: JavaPlugin,
    private val stockService: StockService,
    private val playerLimitService: PlayerLimitService
) {
    private val nextReset = ConcurrentHashMap<String, Long>()

    init {
        // Global background task: Check for resets every minute (1200 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { checkGlobalResets() }, 100L, 1200L)
    }

    private fun checkGlobalResets() {
        val now = System.currentTimeMillis()
        Query.find<ShopDefinitionEntry>().forEach { definition ->
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
        definition.items.forEachIndexed { index, cfg ->
            stockService.reset(definition.id, index, cfg.strategy.stockMax)
        }
        playerLimitService.reset(definition)
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
                val base = zoned.withHour(policy.hour).withMinute(policy.minute).withSecond(0).withNano(0)
                val next = if (base.toInstant().toEpochMilli() > now) base else base.plusDays(1)
                next.toInstant().toEpochMilli()
            }
            is ResetPolicy.Weekly -> {
                val base = zoned.with(TemporalAdjusters.nextOrSame(policy.day))
                    .withHour(policy.hour).withMinute(policy.minute).withSecond(0).withNano(0)
                val next = if (base.toInstant().toEpochMilli() > now) base else base.plusWeeks(1)
                next.toInstant().toEpochMilli()
            }
            is ResetPolicy.Monthly -> {
                val day = policy.day
                val base = zoned.withDayOfMonth(day.coerceIn(1, zoned.toLocalDate().lengthOfMonth()))
                    .withHour(policy.hour).withMinute(policy.minute).withSecond(0).withNano(0)
                val next = if (base.toInstant().toEpochMilli() > now) base else base.plusMonths(1)
                    .withDayOfMonth(day.coerceIn(1, base.plusMonths(1).toLocalDate().lengthOfMonth()))
                next.toInstant().toEpochMilli()
            }
            is ResetPolicy.Cron -> {
                // Simplified: Cron implementation would typically use a library like Quartz or CronUtils.
                // For now, treat as daily at midnight if not implemented.
                zoned.plusDays(1).withHour(0).withMinute(0).toInstant().toEpochMilli()
            }
        }
    }
}
