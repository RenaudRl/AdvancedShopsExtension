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
    override fun balanceOrNull(playerId: UUID): Double? = persistence.getPointsBalance(playerId)

    override fun withdraw(playerId: UUID, amount: Double): Boolean {
        if (amount <= 0.0) return true
        val current = balanceOrNull(playerId) ?: return false
        if (current + Economy.BALANCE_EPSILON < amount) return false
        // Clamped so the epsilon tolerance above can never persist a negative balance.
        persistence.setPointsBalance(playerId, (current - amount).coerceAtLeast(0.0))
        return true
    }

    override fun deposit(playerId: UUID, amount: Double) {
        if (amount <= 0.0) return
        persistence.setPointsBalance(playerId, getBalance(playerId) + amount)
    }
}
