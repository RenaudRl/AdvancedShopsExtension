package com.btc.shops.api

import net.milkbowl.vault.economy.Economy as Vault
import org.bukkit.Bukkit
import java.util.UUID

/**
 * Adapter for Vault based economies.
 *
 * Vault's contract does not forbid a provider from honouring a withdrawal that overdraws the
 * account: several popular providers (and any provider configured to allow debt) return a
 * successful [net.milkbowl.vault.economy.EconomyResponse] while pushing the balance below zero.
 * Trusting `transactionSuccess()` alone therefore let players buy with an empty wallet, so the
 * balance is checked here before the money is asked for.
 */
class VaultEconomy(private val vault: Vault) : Economy {
    override fun balanceOrNull(playerId: UUID): Double? =
        runCatching { vault.getBalance(Bukkit.getOfflinePlayer(playerId)) }.getOrNull()

    override fun canAfford(playerId: UUID, amount: Double): Boolean {
        if (amount <= 0.0) return true
        val offlinePlayer = Bukkit.getOfflinePlayer(playerId)
        // `has` is the provider's own answer and honours its rounding rules; the balance check
        // behind it catches providers whose `has` is permissive.
        val providerSaysYes = runCatching { vault.has(offlinePlayer, amount) }.getOrDefault(false)
        if (!providerSaysYes) return false
        val balance = balanceOrNull(playerId) ?: return false
        return balance + Economy.BALANCE_EPSILON >= amount
    }

    override fun withdraw(playerId: UUID, amount: Double): Boolean {
        if (amount <= 0.0) return true
        if (!canAfford(playerId, amount)) return false
        val result = runCatching {
            vault.withdrawPlayer(Bukkit.getOfflinePlayer(playerId), amount)
        }.getOrNull() ?: return false
        return result.transactionSuccess()
    }

    override fun deposit(playerId: UUID, amount: Double) {
        if (amount <= 0.0) return
        runCatching { vault.depositPlayer(Bukkit.getOfflinePlayer(playerId), amount) }
    }
}
