package com.btc.shops.api

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import java.util.UUID

/**
 * Economy implementation backed by PlaceholderAPI.
 *
 * It retrieves balances from a configured placeholder and executes console commands to deposit or
 * withdraw funds. The resolved text is read by [BalanceParser], which understands the formatting
 * economy plugins apply (grouping separators, currency symbols, `12.5k` shorthand).
 *
 * The withdrawal itself is a fire-and-forget console command: nothing here can observe whether the
 * backing plugin honoured it, so the affordability check before it is the only thing standing
 * between a player and a negative balance.
 */
class PlaceholderEconomy(
    private val balancePlaceholder: String,
    private val addCommand: String,
    private val removeCommand: String
) : Economy {
    override fun balanceOrNull(playerId: UUID): Double? {
        val player = Bukkit.getPlayer(playerId) ?: return null
        return BalanceParser.parse(PlaceholderAPI.setPlaceholders(player, balancePlaceholder))
    }

    override fun withdraw(playerId: UUID, amount: Double): Boolean {
        if (amount <= 0.0) return true
        val player = Bukkit.getPlayer(playerId) ?: return false
        if (!canAfford(playerId, amount)) return false
        dispatch(removeCommand, player.name, amount)
        return true
    }

    override fun deposit(playerId: UUID, amount: Double) {
        if (amount <= 0.0) return
        val player = Bukkit.getPlayer(playerId) ?: return
        dispatch(addCommand, player.name, amount)
    }

    private fun dispatch(template: String, playerName: String, amount: Double) {
        val command = template
            .replace("{player}", playerName)
            .replace("{amount}", amount.toString())
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
    }
}
