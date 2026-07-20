package com.btc.shops.service

import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.manifest.ShopItemConfig
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.entry.matches
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer

/**
 * Lightweight command executor that delegates to [ShopTransactionHandler].
 * Registered via the ShopsInitializer.
 * Folia-safe: all operations run on the player's region thread via the GUI click context.
 */
@Singleton
class ShopCommandExecutor(
    private val plugin: JavaPlugin,
    private val transactionHandler: ShopTransactionHandler,
    private val shopGuiService: ShopGuiService,
    private val resetService: ResetService,
    private val stockService: StockService
) : CommandExecutor {

    companion object {
        const val COMMAND = "typewritershop"
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.size < 4) return false
        val action = args[0]
        val shopId = args[1]
        val itemIndex = args[2].toIntOrNull() ?: return false
        val playerName = args[3]
        val player = Bukkit.getPlayer(playerName) ?: return false
        val amount = args.getOrNull(4)?.toIntOrNull() ?: 1

        val definition = Query.findById<ShopDefinitionEntry>(shopId) ?: return false
        val cfg = definition.items.getOrNull(itemIndex) ?: return false

        // Run on player's scheduler for Folia safety
        player.scheduler.run(plugin, Consumer {
            if (!definition.criteria.matches(player)) {
                player.sendMessage("<red>You cannot access this shop")
                return@Consumer
            }

            if (resetService.shouldReset(definition)) {
                definition.items.forEachIndexed { i, c ->
                    stockService.reset(definition.id, i, c.dynamicPricing.stockMax)
                }
            }

            when (action) {
                "buy" -> transactionHandler.handleBuy(player, cfg, definition, amount)
                "sell" -> transactionHandler.handleSell(player, cfg, definition, amount)
                "buystack" -> {
                    val stackAmount = cfg.item.get(player).build(player).maxStackSize
                    transactionHandler.handleBuy(player, cfg, definition, stackAmount)
                }
                "sellstack" -> {
                    val stackAmount = cfg.item.get(player).build(player).maxStackSize
                    transactionHandler.handleSell(player, cfg, definition, stackAmount)
                }
                "sellall" -> transactionHandler.handleSellAll(player, cfg, definition)
                "buymax" -> transactionHandler.handleBuyMax(player, cfg, definition)
                "submenu_buy" -> shopGuiService.openSubMenu(player, definition, cfg, itemIndex, buy = true)
                "submenu_sell" -> shopGuiService.openSubMenu(player, definition, cfg, itemIndex, buy = false)
                "next_page" -> shopGuiService.navigateNext(player)
                "prev_page" -> shopGuiService.navigatePrev(player)
                "info" -> {
                    definition.infoMessage.forEach { msg ->
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg))
                    }
                }
            }
        }, null)

        return true
    }
}
