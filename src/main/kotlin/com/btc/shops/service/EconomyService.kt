package com.btc.shops.service

import com.btc.shops.api.Economy
import com.btc.shops.api.PlaceholderEconomy
import com.btc.shops.api.PointsEconomy
import com.btc.shops.api.VaultEconomy
import com.btc.shops.manifest.CurrencyType
import com.btc.shops.manifest.ShopDefinitionEntry
import com.typewritermc.core.extension.annotations.Singleton
import org.bukkit.Bukkit

/**
 * Resolves the appropriate [Economy] implementation for a shop definition.
 */
@Singleton
class EconomyService(
    private val persistence: ShopPersistenceService
) {
    fun resolve(definition: ShopDefinitionEntry): Economy? = when (definition.currency) {
        CurrencyType.VAULT -> resolveVault()
        CurrencyType.PLACEHOLDER -> if (definition.balancePlaceholder.isNotBlank() &&
            definition.addCommand.isNotBlank() && definition.removeCommand.isNotBlank()) {
            PlaceholderEconomy(definition.balancePlaceholder, definition.addCommand, definition.removeCommand)
        } else {
            null
        }
        CurrencyType.POINTS -> PointsEconomy(persistence)
    }

    /**
     * Vault's API is `compileOnly`, so on a server without Vault the class simply is not there:
     * touching it threw NoClassDefFoundError instead of returning null, and every transaction on
     * a VAULT shop blew up. Check the plugin is present first, and treat any linkage failure as
     * "no economy available" so the shop reports it cleanly via noEconomyMessage.
     */
    private fun resolveVault(): Economy? {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null
        return runCatching {
            Bukkit.getServicesManager()
                .getRegistration(net.milkbowl.vault.economy.Economy::class.java)
                ?.provider?.let { VaultEconomy(it) }
        }.getOrNull()
    }
}
