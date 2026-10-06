package com.btc.shops.service

import java.util.UUID

/**
 * Storage keys of an item's stock in the shop artifact.
 *
 * A shared stock and a per-player stock of the same item must never share a key, and a reset of
 * one kind must never touch the other: the key shape is what guarantees both.
 */
internal object StockKeys {
    private const val PREFIX = "stock:"
    private val PLAYER_SUFFIX = Regex("""\d+:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")

    /** Key of the stock of item [itemId]: the shared one when [playerUuid] is null, else that player's. */
    fun of(shopId: String, itemId: Int, playerUuid: UUID?): String =
        if (playerUuid != null) "$PREFIX$shopId:$itemId:$playerUuid" else "$PREFIX$shopId:$itemId"

    /** Whether [key] is the stock of one player of [shopId] (and not the shared stock). */
    fun isPlayerStockOf(shopId: String, key: String): Boolean {
        val prefix = "$PREFIX$shopId:"
        return key.startsWith(prefix) && PLAYER_SUFFIX.matches(key.substring(prefix.length))
    }
}
