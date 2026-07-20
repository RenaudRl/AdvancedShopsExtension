package com.btc.shops.service

import com.btc.shops.manifest.ShopDefinitionEntry
import com.typewritermc.core.extension.annotations.Singleton

/**
 * Tracks global limits (across all players) for shop items.
 */
@Singleton
class GlobalLimitService(
    private val persistence: ShopPersistenceService
) {
    fun reset(definition: ShopDefinitionEntry) {
        persistence.resetGlobalLimits(definition.id)
    }

    fun resetGlobal(definition: ShopDefinitionEntry, type: String) {
        persistence.resetGlobalLimitsByType(definition.id)
    }

    fun remaining(definition: ShopDefinitionEntry, index: Int, limit: Int): Int {
        if (limit <= 0) return Int.MAX_VALUE
        val used = persistence.getGlobalLimit(definition.id, index)
        return (limit - used).coerceAtLeast(0)
    }

    fun remainingGlobal(definition: ShopDefinitionEntry, type: String, limit: Int): Int {
        if (limit <= 0) return Int.MAX_VALUE
        val used = persistence.getGlobalLimitByType(definition.id, type)
        return (limit - used).coerceAtLeast(0)
    }

    fun record(definition: ShopDefinitionEntry, index: Int, amount: Int) {
        if (amount <= 0) return
        val current = persistence.getGlobalLimit(definition.id, index)
        persistence.setGlobalLimit(definition.id, index, current + amount)
    }

    fun recordGlobal(definition: ShopDefinitionEntry, type: String, amount: Int) {
        if (amount <= 0) return
        val current = persistence.getGlobalLimitByType(definition.id, type)
        persistence.setGlobalLimitByType(definition.id, type, current + amount)
    }
}
