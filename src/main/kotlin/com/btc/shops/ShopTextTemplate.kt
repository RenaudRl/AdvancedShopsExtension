package com.btc.shops

/**
 * Fills the `{name}` placeholders of the texts a server owner edits (the snippets of [ShopTexts], the trend and pool lore fields).
 *
 * Kept free of the engine so the wording logic runs in plain unit tests.
 */
internal object ShopTextTemplate {
    private val placeholder = Regex("""\{(\w+)}""")

    /**
     * Replaces each `{name}` of [template] with its value from [values] in a single pass: a value that itself contains
     * `{something}` (a shop name an admin wrote) is never filled a second time. A placeholder with no value stays as written,
     * so a token meant for a later step (`{buy}` in a lore line) survives.
     */
    fun fill(template: String, values: Map<String, String>): String =
        placeholder.replace(template) { match -> values[match.groupValues[1]] ?: match.value }
}
