package com.btc.shops.service

import com.btc.shops.manifest.ShopArtifact
import com.typewritermc.core.extension.annotations.Singleton
import java.util.UUID

/**
 * Central persistence service for all shop runtime data.
 * Delegates to [ShopArtifactPersistenceService] for AssetManager-based load/save.
 */
@Singleton
class ShopPersistenceService(
    private val artifactPersistence: ShopArtifactPersistenceService
) {

    // ── Stock ──

    fun getStock(shopId: String, itemId: Int, max: Int, playerUuid: UUID? = null): Int {
        val data = artifactPersistence.load()
        val key = stockKey(shopId, itemId, playerUuid)
        return data.items[key]?.stock ?: max
    }

    fun setStock(shopId: String, itemId: Int, stock: Int, playerUuid: UUID? = null) {
        artifactPersistence.update { data ->
            val key = stockKey(shopId, itemId, playerUuid)
            val state = data.items[key] ?: ShopArtifact.ShopItemState()
            data.items[key] = state.copy(stock = stock, lastUpdate = System.currentTimeMillis())
        }
    }

    fun resetStock(shopId: String, itemId: Int, max: Int, playerUuid: UUID? = null) {
        setStock(shopId, itemId, max, playerUuid)
    }

    // ── Player Limits ──

    fun getPlayerLimit(shopId: String, itemId: Int, playerUuid: UUID): Int {
        val data = artifactPersistence.load()
        val key = "limit:$shopId:$itemId:$playerUuid"
        return data.items[key]?.demandBuy ?: 0
    }

    fun setPlayerLimit(shopId: String, itemId: Int, playerUuid: UUID, used: Int) {
        artifactPersistence.update { data ->
            val key = "limit:$shopId:$itemId:$playerUuid"
            val state = data.items[key] ?: ShopArtifact.ShopItemState()
            data.items[key] = state.copy(demandBuy = used, lastUpdate = System.currentTimeMillis())
        }
    }

    fun resetPlayerLimits(shopId: String) {
        artifactPersistence.update { data ->
            val prefix = "limit:$shopId:"
            data.items.keys.removeIf { it.startsWith(prefix) }
        }
    }

    // ── Global Limits ──

    fun getGlobalLimit(shopId: String, itemId: Int): Int {
        val data = artifactPersistence.load()
        val key = "global:$shopId:$itemId"
        return data.items[key]?.demandBuy ?: 0
    }

    fun setGlobalLimit(shopId: String, itemId: Int, used: Int) {
        artifactPersistence.update { data ->
            val key = "global:$shopId:$itemId"
            val state = data.items[key] ?: ShopArtifact.ShopItemState()
            data.items[key] = state.copy(demandBuy = used, lastUpdate = System.currentTimeMillis())
        }
    }

    fun getGlobalLimitByType(shopId: String, type: String): Int {
        val data = artifactPersistence.load()
        val key = "global:$shopId:$type"
        return data.items[key]?.demandBuy ?: 0
    }

    fun setGlobalLimitByType(shopId: String, type: String, used: Int) {
        artifactPersistence.update { data ->
            val key = "global:$shopId:$type"
            val state = data.items[key] ?: ShopArtifact.ShopItemState()
            data.items[key] = state.copy(demandBuy = used, lastUpdate = System.currentTimeMillis())
        }
    }

    fun resetGlobalLimits(shopId: String) {
        artifactPersistence.update { data ->
            val prefix = "global:$shopId:"
            data.items.keys.removeIf { key ->
                key.startsWith(prefix) && !key.substring(prefix.length).contains(":")
            }
        }
    }

    fun resetGlobalLimitsByType(shopId: String) {
        artifactPersistence.update { data ->
            val prefix = "global:$shopId:"
            data.items.keys.removeIf { key ->
                key.startsWith(prefix) && key.substring(prefix.length).contains(":")
            }
        }
    }

    // ── Points Economy ──

    fun getPointsBalance(playerUuid: UUID): Double {
        val data = artifactPersistence.load()
        val key = "points:$playerUuid"
        return data.items[key]?.priceState ?: 0.0
    }

    fun setPointsBalance(playerUuid: UUID, balance: Double) {
        artifactPersistence.update { data ->
            val key = "points:$playerUuid"
            val state = data.items[key] ?: ShopArtifact.ShopItemState()
            data.items[key] = state.copy(priceState = balance, lastUpdate = System.currentTimeMillis())
        }
    }

    // ── Helpers ──

    private fun stockKey(shopId: String, itemId: Int, playerUuid: UUID?): String {
        return if (playerUuid != null) "stock:$shopId:$itemId:$playerUuid" else "stock:$shopId:$itemId"
    }
}
