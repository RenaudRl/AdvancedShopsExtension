package com.btc.shops.service

import com.btc.shops.manifest.ShopDefinitionEntry
import com.typewritermc.core.extension.annotations.Singleton
import org.bukkit.entity.Player
import java.util.UUID

@Singleton
class PlayerLimitService(
    private val persistence: ShopPersistenceService
) {
    fun reset(definition: ShopDefinitionEntry) {
        persistence.resetPlayerLimits(definition.id)
    }

    fun remaining(player: Player, definition: ShopDefinitionEntry, index: Int, limit: Int): Int {
        if (limit <= 0) return Int.MAX_VALUE
        val used = persistence.getPlayerLimit(definition.id, index, player.uniqueId)
        return limit - used
    }

    fun record(player: Player, definition: ShopDefinitionEntry, index: Int, amount: Int) {
        if (amount <= 0) return
        val current = persistence.getPlayerLimit(definition.id, index, player.uniqueId)
        persistence.setPlayerLimit(definition.id, index, player.uniqueId, current + amount)
    }
}
