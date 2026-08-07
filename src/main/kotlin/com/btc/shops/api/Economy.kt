package com.btc.shops.api

import java.util.UUID

/**
 * Abstraction over any currency implementation.
 * Provides minimal operations required by the shop system.
 */
interface Economy {
    /**
     * Balance of the player, or `null` when it cannot be read.
     *
     * "Unknown" and "zero" are not the same thing: a placeholder that fails to parse, an offline
     * player or an economy provider that errors out must never be mistaken for an empty wallet,
     * because [canAfford] would then silently let the purchase through on a shop priced at 0.
     */
    fun balanceOrNull(playerId: UUID): Double?

    /**
     * Balance of the player, `0.0` when unknown.
     *
     * Kept for display and for the public [ShopAPI]; anything that decides whether a transaction
     * may happen must go through [canAfford] or [balanceOrNull] instead.
     */
    fun getBalance(playerId: UUID): Double = balanceOrNull(playerId) ?: 0.0

    /**
     * Whether the player can pay [amount] without going below zero.
     *
     * An unreadable balance answers `false`: refusing a legitimate purchase is recoverable,
     * handing out goods against a negative balance is not.
     */
    fun canAfford(playerId: UUID, amount: Double): Boolean {
        if (amount <= 0.0) return true
        val balance = balanceOrNull(playerId) ?: return false
        return balance + BALANCE_EPSILON >= amount
    }

    /**
     * Withdraws [amount] from the player, returning `false` without moving any money when the
     * player cannot afford it. Implementations must never leave a balance below zero.
     */
    fun withdraw(playerId: UUID, amount: Double): Boolean

    /** Deposits [amount] to the player. */
    fun deposit(playerId: UUID, amount: Double)

    companion object {
        /**
         * Prices are carried as doubles and displayed rounded to the cent, so an exact-change
         * purchase can land a few ulps short of its own price. This tolerance absorbs that noise
         * and stays far below the smallest representable currency unit.
         */
        const val BALANCE_EPSILON = 1e-6
    }
}
