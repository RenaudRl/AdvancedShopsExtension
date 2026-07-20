package com.btc.shops.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Cron expression helper supporting standard 5-field cron expressions.
 * Supports wildcard ({@literal *}), ranges (1-5), step values ({@literal *}/2, 1-10/2), lists (1,3,5), and single values.
 */
object CronUtil {

    /**
     * Calculate the next execution time after [now] for the given cron [pattern].
     * Returns [now] as fallback if the pattern cannot be parsed.
     */
    fun next(pattern: String, now: Long = System.currentTimeMillis()): Long {
        val fields = cronFields(pattern)
        if (fields.isEmpty()) return now

        try {
            var candidate = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes(1)
                .toInstant()
                .toEpochMilli()

            // Safety limit to prevent infinite loops
            val maxIterations = 366 * 24 * 60 // ~1 year in minutes
            for (i in 0 until maxIterations) {
                val zdt = Instant.ofEpochMilli(candidate).atZone(ZoneId.systemDefault())
                if (fields[0].matches(zdt.minute) &&
                    fields[1].matches(zdt.hour) &&
                    fields[2].matches(zdt.dayOfMonth) &&
                    fields[3].matches(zdt.monthValue) &&
                    matchesDayOfWeek(fields[4], zdt)
                ) {
                    return candidate
                }
                candidate = Instant.ofEpochMilli(candidate).atZone(ZoneId.systemDefault())
                    .plusMinutes(1).toInstant().toEpochMilli()
            }
        } catch (_: Exception) {
            // Return now as fallback
        }
        return now
    }

    private fun cronFields(patternString: String): List<CronField> {
        return patternString.trim().split("""\s+""".toRegex()).map { parseField(it) }
    }

    private fun parseField(field: String): CronField {
        return when {
            field == "*" -> CronField(start = 0, end = Int.MAX_VALUE, step = 1)
            field.contains("/") -> {
                val (rangePart, stepPart) = field.split("/", limit = 2).let { it[0] to it[1].toIntOrNull() }
                val step = stepPart ?: 1
                when {
                    rangePart == "*" -> CronField(start = 0, end = Int.MAX_VALUE, step = step)
                    rangePart.contains("-") -> {
                        val (s, e) = rangePart.split("-", limit = 2).mapNotNull { it.toIntOrNull() }
                        CronField(start = s, end = e, step = step)
                    }
                    else -> {
                        val start = rangePart.toIntOrNull() ?: 0
                        CronField(start = start, end = Int.MAX_VALUE, step = step)
                    }
                }
            }
            field.contains("-") -> {
                val (s, e) = field.split("-", limit = 2).mapNotNull { it.toIntOrNull() }
                CronField(start = s, end = e, step = 1)
            }
            field.contains(",") -> {
                val values = field.split(",").mapNotNull { it.trim().toIntOrNull() }
                CronField(values = values.toSet())
            }
            else -> {
                val value = field.trim().toIntOrNull() ?: 0
                CronField(values = setOf(value))
            }
        }
    }

    private fun matchesDayOfWeek(field: CronField, zdt: ZonedDateTime): Boolean {
        // Cron uses 0-6 or 1-7 for day of week (0/7 = Sunday)
        val dayOfWeek = zdt.dayOfWeek.value // 1=Monday, 7=Sunday
        // Convert: cron 0=Sunday, 1=Monday, ... 6=Saturday
        //         java 1=Monday, ... 7=Sunday
        val cronDow = when (dayOfWeek) {
            7 -> 0 // Sunday
            else -> dayOfWeek // Monday=1, ... Saturday=6
        }
        return field.matches(cronDow) || (field.matches(0) && dayOfWeek == 7)
    }

    private data class CronField(
        val start: Int = 0,
        val end: Int = Int.MAX_VALUE,
        val step: Int = 1,
        val values: Set<Int>? = null
    ) {
        fun matches(value: Int): Boolean {
            val vals = values
            return if (vals != null) {
                value in vals
            } else {
                value >= start && value <= end && ((value - start) % step == 0)
            }
        }
    }
}
