package com.btc.shops.service

import btcrenaud.gui.api.GuiSlot
import btcrenaud.gui.services.MenuSessionService
import com.btc.shops.manifest.ShopDefinitionEntry
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.engine.paper.entry.matches
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer

/**
 * Handles shop button clicks coming from the GUI extension.
 *
 * Buttons dispatch `shop:<action> <shopId> <itemIndex> [amount]` and this handler is invoked
 * directly by [MenuSessionService], in-process. Previously these were registered as a real Bukkit
 * command (`/typewritershop`) and pushed back through `Bukkit.dispatchCommand` on the console
 * sender — that leaked an internal command into the server's command list and tab completion, and
 * relied on passing the player's *name* through the command string to find them again.
 *
 * Folia-safe: transactions run on the clicking player's scheduler.
 */
@Singleton
class ShopButtonActionHandler(
    private val plugin: JavaPlugin,
    private val transactionHandler: ShopTransactionHandler,
    private val shopGuiService: ShopGuiService,
    private val resetService: ResetService,
    private val stockService: StockService,
) {

    companion object {
        /** Prefix registered with [MenuSessionService.registerCustomCommandHandler]. */
        const val PREFIX = "shop:"
        private const val AMOUNT_INPUT_KEY = "amount"
    }

    /** Registers this handler so `shop:` button commands are routed here instead of the console. */
    fun register() {
        MenuSessionService.registerCustomCommandHandler(PREFIX) { player, _, command, slot, context ->
            handle(player, command, slot, context)
        }
    }

    fun unregister() {
        MenuSessionService.unregisterCustomCommandHandler(PREFIX)
    }

    private fun handle(player: Player, command: String, @Suppress("UNUSED_PARAMETER") slot: GuiSlot?, context: InteractionContext) {
        // shop:<action> <shopId> <itemIndex> [amount]
        val parts = command.removePrefix(PREFIX).trim().split(" ")
        val action = parts.getOrNull(0) ?: return
        val shopId = parts.getOrNull(1) ?: return
        val itemIndex = parts.getOrNull(2)?.toIntOrNull() ?: return
        val amount = parts.getOrNull(3)?.toIntOrNull() ?: 1

        val definition = Query.findById<ShopDefinitionEntry>(shopId) ?: return

        player.scheduler.run(plugin, Consumer {
            if (!definition.criteria.matches(player)) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(definition.criteriaFailMessage))
                return@Consumer
            }

            if (resetService.shouldReset(definition)) {
                definition.items.forEachIndexed { i, c ->
                    stockService.reset(definition.id, i, c.dynamicPricing.stockMax)
                }
            }

            // Navigation needs no item; item actions resolve one first.
            when (action) {
                "next_page" -> {
                    shopGuiService.navigateNext(player)
                    return@Consumer
                }
                "prev_page" -> {
                    shopGuiService.navigatePrev(player)
                    return@Consumer
                }
                "info" -> {
                    definition.infoMessage.forEach { msg ->
                        player.sendMessage(MiniMessage.miniMessage().deserialize(msg))
                    }
                    return@Consumer
                }
            }

            val cfg = definition.items.getOrNull(itemIndex) ?: return@Consumer
            when (action) {
                "buy" -> transactionHandler.handleBuy(player, cfg, definition, amount)
                "sell" -> transactionHandler.handleSell(player, cfg, definition, amount)
                "buystack" -> transactionHandler.handleBuy(player, cfg, definition, cfg.item.get(player).build(player).maxStackSize)
                "sellstack" -> transactionHandler.handleSell(player, cfg, definition, cfg.item.get(player).build(player).maxStackSize)
                "sellall" -> transactionHandler.handleSellAll(player, cfg, definition)
                "buymax" -> transactionHandler.handleBuyMax(player, cfg, definition)
                "submenu_buy" -> shopGuiService.openSubMenu(player, definition, cfg, itemIndex, buy = true)
                "submenu_sell" -> shopGuiService.openSubMenu(player, definition, cfg, itemIndex, buy = false)
                "amount_buy" -> promptAmount(player, definition, itemIndex, buy = true)
                "amount_sell" -> promptAmount(player, definition, itemIndex, buy = false)
            }
        }, null)
    }

    /**
     * Asks the player how many units to trade, then runs the transaction.
     *
     * BUY_CUSTOM / SELL_CUSTOM buttons emitted `amount_buy` / `amount_sell` but nothing ever
     * handled them, so those buttons silently did nothing when clicked.
     */
    private fun promptAmount(player: Player, definition: ShopDefinitionEntry, itemIndex: Int, buy: Boolean) {
        val mm = MiniMessage.miniMessage()
        val title = mm.deserialize(if (buy) definition.buyAmountPrompt else definition.sellAmountPrompt)

        val input = DialogInstancesProvider.instance()
            .textBuilder(AMOUNT_INPUT_KEY, mm.deserialize(definition.amountInputLabel))
            .width(definition.amountInputWidth)
            .maxLength(definition.amountInputMaxLength)
            .initial(definition.amountInputPlaceholder)
            .build()

        MenuSessionService.openPaperDialogInput(
            player,
            title,
            listOf(input),
            mm.deserialize(definition.amountConfirmButton),
        ) { responses ->
            val amount = responses[AMOUNT_INPUT_KEY]?.trim()?.toIntOrNull()
            if (amount == null || amount <= 0) {
                player.sendMessage(mm.deserialize(definition.invalidAmountMessage))
                return@openPaperDialogInput
            }
            val cfg = definition.items.getOrNull(itemIndex) ?: return@openPaperDialogInput
            if (buy) {
                transactionHandler.handleBuy(player, cfg, definition, amount)
            } else {
                transactionHandler.handleSell(player, cfg, definition, amount)
            }
            shopGuiService.refresh(player)
        }
    }
}
