package com.btc.shops.api

import com.btc.shops.service.ShopPersistenceService
import com.typewritermc.core.extension.annotations.Singleton
import java.util.UUID

/**
 * Simple in-memory economy backed by [ShopPersistenceService].
 * Balances survive server restarts via artifact persistence.
 */
@Singleton
class PointsEconomy(
    private val persistence: ShopPersistenceService
) : Economy {
    override fun getBalance(playerId: UUID): Double = persistence.getPointsBalance(playerId)

    override fun withdraw(playerId: UUID, amount: Double): Boolean {
        val current = getBalance(playerId)
        if (current < amount) return false
        persistence.setPointsBalance(playerId, current - amount)
        return true
    }

    override fun deposit(playerId: UUID, amount: Double) {
        persistence.setPointsBalance(playerId, getBalance(playerId) + amount)
    }
}
