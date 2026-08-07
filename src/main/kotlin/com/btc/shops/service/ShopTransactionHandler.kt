package com.btc.shops.service

import com.btc.shops.api.Economy
import com.btc.shops.manifest.PriceMode
import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.manifest.ShopItemConfig
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.utils.Sound
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer

/**
 * Handles all buy/sell transactions.
 * Replaces the legacy ShopInteractionHandler command-based approach.
 * All operations are Folia-safe (called from GUI click handlers on the player's region thread).
 */
@Singleton
class ShopTransactionHandler(
    private val plugin: JavaPlugin,
    private val economyService: EconomyService,
    private val priceService: PriceService,
    private val stockService: StockService,
    private val resetService: ResetService,
    private val playerLimitService: PlayerLimitService,
    private val globalLimitService: GlobalLimitService,
    private val taxService: TaxService,
    private val promotionService: PromotionService,
    private val priceTrendService: PriceTrendService,
    private val transactionLogService: TransactionLogService,
    private val shopGuiService: ShopGuiService
) {
    private val miniMessage = MiniMessage.miniMessage()

    private fun String.toComponent() = miniMessage.deserialize(this)
    private fun formatPrice(value: Double): String = String.format("%.2f", value)

    fun handleBuy(
        player: Player,
        cfg: ShopItemConfig,
        definition: ShopDefinitionEntry,
        amount: Int
    ) {
        if (!cfg.buyEnabled) {
            sendDenied(player, definition)
            return
        }
        val index = definition.items.indexOf(cfg)

        // Check player limit
        if (cfg.playerLimit > 0) {
            val remaining = playerLimitService.remaining(player, definition, index, cfg.playerLimit)
            if (remaining < amount) {
                player.sendMessage(definition.limitReachedMessage.toComponent())
                playSound(player, definition.deniedSound)
                return
            }
        }

        // Check global limit
        if (cfg.globalLimit > 0) {
            val remaining = globalLimitService.remaining(definition, index, cfg.globalLimit)
            if (remaining < amount) {
                player.sendMessage(definition.limitReachedMessage.toComponent())
                playSound(player, definition.deniedSound)
                return
            }
        }

        // Check global buy limit
        if (definition.globalBuyLimit > 0) {
            val remaining = globalLimitService.remainingGlobal(definition, "buy", definition.globalBuyLimit)
            if (remaining < amount) {
                player.sendMessage(definition.limitReachedMessage.toComponent())
                playSound(player, definition.deniedSound)
                return
            }
        }

        val baseCost = calcBuyPrice(definition, cfg, amount)
        val promotedCost = promotionService.getPromotedPrice(baseCost, definition.id, index)
        val taxedCost = taxService.applyBuyTax(promotedCost, definition, cfg)

        val economy = economyService.resolve(definition)
        if (economy == null) {
            player.sendMessage(definition.noEconomyMessage.toComponent())
            playSound(player, definition.deniedSound)
            return
        }

        // Two gates on purpose. `canAfford` is the one that produces the player-facing refusal,
        // and `withdraw` re-checks internally so no economy adapter can ever settle a purchase
        // that leaves the player below zero.
        if (!economy.canAfford(player.uniqueId, taxedCost) ||
            !economy.withdraw(player.uniqueId, taxedCost)
        ) {
            player.sendMessage(
                definition.cannotAffordMessage.replace("{amount}", amount.toString())
                    .replace("{price}", formatPrice(taxedCost)).toComponent()
            )
            playSound(player, definition.deniedSound)
            return
        }

        // ATOMIC: Check inventory space BEFORE giving items
        val item = cfg.item.get(player).build(player).clone()
        val totalAmount = amount
        val maxStack = item.maxStackSize
        val fullStacks = totalAmount / maxStack
        val remainder = totalAmount % maxStack

        // Check if player can receive all items
        val availableSpace = player.inventory.contents.filterNotNull().sumOf { slot ->
            if (slot.type == Material.AIR || slot.type.isAir) maxStack
            else if (slot.isSimilar(item)) maxStack - slot.amount
            else 0
        }
        // Also count empty slots
        val emptySlots = player.inventory.contents.count { it == null || it.type == Material.AIR || it.type.isAir }
        val totalAvailable = availableSpace + emptySlots * maxStack

        if (totalAvailable < totalAmount) {
            // Refund
            economy.deposit(player.uniqueId, taxedCost)
            player.sendMessage(
                definition.inventoryFullMessage.replace("{amount}", amount.toString())
                    .replace("{price}", formatPrice(taxedCost)).toComponent()
            )
            playSound(player, definition.deniedSound)
            return
        }

        // Give items
        item.amount = totalAmount
        player.inventory.addItem(item)

        // Record transaction
        stockService.buy(definition.id, index, amount, cfg.dynamicPricing)
        if (cfg.playerLimit > 0) playerLimitService.record(player, definition, index, amount)
        if (cfg.globalLimit > 0) globalLimitService.record(definition, index, amount)
        if (definition.globalBuyLimit > 0) globalLimitService.recordGlobal(definition, "buy", amount)

        // Log transaction
        transactionLogService.log(
            TransactionRecord(
                playerUuid = player.uniqueId,
                shopId = definition.id,
                itemIndex = index,
                type = "buy",
                amount = amount,
                price = taxedCost
            )
        )

        // Record price trend
        priceTrendService.recordPrice(definition.id, index, taxedCost / amount)

        player.sendMessage(
            definition.purchaseMessage.replace("{amount}", amount.toString())
                .replace("{price}", formatPrice(taxedCost)).toComponent()
        )
        playSound(player, definition.buySound)
        shopGuiService.refresh(player)
    }

    fun handleSell(
        player: Player,
        cfg: ShopItemConfig,
        definition: ShopDefinitionEntry,
        amount: Int
    ) {
        if (!cfg.sellEnabled) {
            player.sendMessage(definition.cannotSellMessage.toComponent())
            playSound(player, definition.deniedSound)
            return
        }
        val index = definition.items.indexOf(cfg)

        val baseReward = calcSellPrice(definition, cfg, amount)
        if (baseReward <= 0.0) {
            player.sendMessage(
                definition.cannotSellMessage.replace("{amount}", amount.toString())
                    .replace("{price}", formatPrice(baseReward)).toComponent()
            )
            playSound(player, definition.deniedSound)
            return
        }

        val template = cfg.item.get(player).build(player).clone()
        template.amount = 1
        val validCount = countMatchingItems(player, template)
        if (validCount < amount) {
            player.sendMessage(
                definition.notEnoughItemsMessage.replace("{amount}", amount.toString())
                    .replace("{price}", formatPrice(baseReward)).toComponent()
            )
            playSound(player, definition.deniedSound)
            return
        }

        // Remove items
        removeMatchingItems(player, template, amount)

        val promotedReward = promotionService.getPromotedPrice(baseReward, definition.id, index)
        val taxedReward = taxService.applySellTax(promotedReward, definition, cfg)

        val economy = economyService.resolve(definition)
        if (economy != null) {
            economy.deposit(player.uniqueId, taxedReward)
        }

        stockService.sell(definition.id, index, amount, cfg.dynamicPricing)

        // Log transaction
        transactionLogService.log(
            TransactionRecord(
                playerUuid = player.uniqueId,
                shopId = definition.id,
                itemIndex = index,
                type = "sell",
                amount = amount,
                price = taxedReward
            )
        )

        // Record price trend
        priceTrendService.recordPrice(definition.id, index, taxedReward / amount)

        player.sendMessage(
            definition.sellMessage.replace("{amount}", amount.toString())
                .replace("{price}", formatPrice(taxedReward)).toComponent()
        )
        playSound(player, definition.sellSound)
        shopGuiService.refresh(player)
    }

    fun handleSellAll(
        player: Player,
        cfg: ShopItemConfig,
        definition: ShopDefinitionEntry
    ) {
        if (!cfg.sellEnabled) return
        val template = cfg.item.get(player).build(player).clone()
        template.amount = 1
        val allAmount = countMatchingItems(player, template)
        if (allAmount <= 0) {
            player.sendMessage(definition.notEnoughItemsMessage.toComponent())
            playSound(player, definition.deniedSound)
            return
        }
        handleSell(player, cfg, definition, allAmount)
    }

    fun handleBuyMax(
        player: Player,
        cfg: ShopItemConfig,
        definition: ShopDefinitionEntry
    ) {
        if (!cfg.buyEnabled || !cfg.buyMaxEnabled) return
        val index = definition.items.indexOf(cfg)

        val economy = economyService.resolve(definition) ?: return
        val unitPrice = when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedBuyPrice
            PriceMode.DYNAMIC -> {
                val stock = stockService.getStock(definition.id, index, cfg.dynamicPricing.stockMax)
                priceService.calculateBuyPrice(stock, cfg.dynamicPricing)
            }
        }

        if (unitPrice <= 0.0) return

        // An unreadable balance buys nothing: `handleBuy` would refuse anyway, but reporting it
        // here keeps "buy max" from silently doing nothing at all.
        val balance = economy.balanceOrNull(player.uniqueId)
        if (balance == null) {
            player.sendMessage(definition.cannotAffordMessage.toComponent())
            playSound(player, definition.deniedSound)
            return
        }
        val taxRate = if (definition.taxRate > 0) (1.0 + definition.taxRate / 100.0) else 1.0
        val maxByBalance = (balance / (unitPrice * taxRate)).toInt()

        val template = cfg.item.get(player).build(player).clone()
        template.amount = 1
        val maxStack = template.maxStackSize
        val emptySlots = player.inventory.contents.count { it == null || it.type == Material.AIR || it.type.isAir }
        val maxByInventory = emptySlots * maxStack

        var maxByLimits = Int.MAX_VALUE
        if (cfg.playerLimit > 0) {
            maxByLimits = minOf(maxByLimits, playerLimitService.remaining(player, definition, index, cfg.playerLimit))
        }
        if (cfg.globalLimit > 0) {
            maxByLimits = minOf(maxByLimits, globalLimitService.remaining(definition, index, cfg.globalLimit))
        }
        if (definition.globalBuyLimit > 0) {
            maxByLimits = minOf(maxByLimits, globalLimitService.remainingGlobal(definition, "buy", definition.globalBuyLimit))
        }

        // Never floor this to 1: doing so turned "you can afford none of these" into a forced
        // single purchase, which is how an empty wallet ended up in the negative.
        val maxAmount = minOf(maxByBalance, maxByInventory, maxByLimits)
        if (maxAmount <= 0) {
            val message =
                if (maxByBalance <= 0) definition.cannotAffordMessage
                else if (maxByInventory <= 0) definition.inventoryFullMessage
                else definition.limitReachedMessage
            player.sendMessage(
                message.replace("{amount}", "1")
                    .replace("{price}", formatPrice(unitPrice * taxRate)).toComponent()
            )
            playSound(player, definition.deniedSound)
            return
        }
        handleBuy(player, cfg, definition, maxAmount)
    }

    private fun sendDenied(player: Player, definition: ShopDefinitionEntry) {
        player.sendMessage(definition.cannotAffordMessage.toComponent())
        playSound(player, definition.deniedSound)
    }

    private fun countMatchingItems(player: Player, template: ItemStack): Int {
        return player.inventory.contents.filterNotNull().filter { item ->
            if (!item.isSimilar(template)) return@filter false
            val meta = item.itemMeta
            if (meta != null && meta.hasItemModel()) {
                val templateMeta = template.itemMeta
                if (templateMeta != null && templateMeta.hasItemModel()) {
                    if (meta.itemModel != templateMeta.itemModel) return@filter false
                }
            }
            true
        }.sumOf { it.amount }
    }

    private fun removeMatchingItems(player: Player, template: ItemStack, amount: Int) {
        var remaining = amount
        for (i in player.inventory.contents.indices) {
            val item = player.inventory.contents[i] ?: continue
            if (!item.isSimilar(template)) continue
            val toRemove = minOf(item.amount, remaining)
            if (toRemove >= item.amount) {
                player.inventory.setItem(i, null)
            } else {
                item.amount = item.amount - toRemove
            }
            remaining -= toRemove
            if (remaining <= 0) break
        }
    }

    private fun calcBuyPrice(definition: ShopDefinitionEntry, cfg: ShopItemConfig, amount: Int): Double {
        return when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedBuyPrice * amount
            PriceMode.DYNAMIC -> {
                val index = definition.items.indexOf(cfg)
                val stock = stockService.getStock(definition.id, index, cfg.dynamicPricing.stockMax)
                priceService.calculateBuyPrice(stock, cfg.dynamicPricing) * amount
            }
        }
    }

    private fun calcSellPrice(definition: ShopDefinitionEntry, cfg: ShopItemConfig, amount: Int): Double {
        return when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedSellPrice * amount
            PriceMode.DYNAMIC -> {
                val index = definition.items.indexOf(cfg)
                val stock = stockService.getStock(definition.id, index, cfg.dynamicPricing.stockMax)
                priceService.calculateSellPrice(stock, cfg.dynamicPricing) * amount
            }
        }
    }

    private fun playSound(player: Player, sound: Sound?) {
        if (sound == null) return
        val ctx = context {}
        sound.play(player, ctx)
    }
}
