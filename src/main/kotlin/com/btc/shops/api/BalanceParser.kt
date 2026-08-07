package com.btc.shops.api

/**
 * Parses the human-readable balance a placeholder hands back.
 *
 * Economy plugins format balances for players, not for parsers: `$1,234.56`, `1 234,56 EUR`,
 * `1.234,56`, `12.5k`. A plain `toDoubleOrNull()` returns `null` on all of those, and the old
 * `?: 0.0` fallback turned "I could not read this" into "this player is broke" — which either
 * blocks a solvent player or, on a free item, lets an insolvent one through. Everything that
 * cannot be understood returns `null` so the caller can refuse explicitly.
 */
object BalanceParser {
    private val SUFFIX_MULTIPLIERS = mapOf(
        'k' to 1_000.0,
        'm' to 1_000_000.0,
        'b' to 1_000_000_000.0,
        't' to 1_000_000_000_000.0,
    )

    /**
     * Space-like characters formatters use as a thousands separator: plain space, tab,
     * no-break space (U+00A0), narrow no-break space (U+202F) and thin space (U+2009).
     */
    private const val SPACES = " \t   "

    fun parse(raw: String?): Double? {
        if (raw == null) return null
        var text = raw.trim()
        if (text.isEmpty()) return null

        text = text.filterNot { it in SPACES }
        if (text.isEmpty()) return null

        // Accounting notation puts negatives in parentheses; a leading minus is the usual form.
        val negative = text.startsWith('-') || (text.startsWith('(') && text.endsWith(')'))
        text = text.trim('(', ')', '-', '+')

        // A trailing magnitude suffix ("12.5k") is consumed before the digits are read; anything
        // else non-numeric (currency symbols, codes, stray markup) is simply dropped.
        var multiplier = 1.0
        val suffix = text.lastOrNull()?.takeIf { it.isLetter() }?.lowercaseChar()
        if (suffix != null) {
            SUFFIX_MULTIPLIERS[suffix]?.let {
                multiplier = it
                text = text.dropLast(1)
            }
        }

        text = text.filter { it.isDigit() || it == '.' || it == ',' }
        if (text.none { it.isDigit() }) return null

        val normalized = normalizeSeparators(text) ?: return null
        val value = normalized.toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null

        val magnitude = value * multiplier
        return if (negative) -magnitude else magnitude
    }

    /**
     * Collapses `.` and `,` into a single decimal point.
     *
     * With both present, the rightmost one is the decimal separator (true for every locale that
     * uses both). With only one present, a repeated separator is grouping, and a single one is
     * grouping only when it is followed by exactly three digits — the standard ambiguity call
     * that reads `1,234` as one thousand and `12,34` as twelve and change.
     */
    private fun normalizeSeparators(text: String): String? {
        val lastDot = text.lastIndexOf('.')
        val lastComma = text.lastIndexOf(',')

        val collapsed = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimal = if (lastDot > lastComma) '.' else ','
                val grouping = if (decimal == '.') ',' else '.'
                text.filterNot { it == grouping }.replace(decimal, '.')
            }
            lastDot < 0 && lastComma < 0 -> text
            else -> {
                val separator = if (lastDot >= 0) '.' else ','
                val position = maxOf(lastDot, lastComma)
                val occurrences = text.count { it == separator }
                val trailingDigits = text.length - position - 1
                if (occurrences > 1 || trailingDigits == 3) text.filterNot { it == separator }
                else text.replace(separator, '.')
            }
        }
        return collapsed.takeIf { it.isNotEmpty() && it.none { char -> char == ',' } }
    }
}
