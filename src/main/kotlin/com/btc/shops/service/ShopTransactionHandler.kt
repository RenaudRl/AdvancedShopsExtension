package com.btc.shops.service

import com.btc.shops.api.Economy
import com.btc.shops.api.ShopTransactionEvent
import com.btc.shops.manifest.PriceMode
import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.manifest.ShopItemConfig
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.utils.Sound
import com.typewritermc.engine.paper.logger
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
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
    private val shopGuiService: ShopGuiService,
    private val itemPriceService: ItemPriceService,
) {
    private val miniMessage = MiniMessage.miniMessage()

    /**
     * Announces a completed transaction to whoever is listening.
     *
     * A listener that throws is a listener's problem: it must not undo a purchase that has already
     * been paid for and delivered, so the failure is reported and the transaction stands.
     */
    private fun announce(
        player: Player,
        definition: ShopDefinitionEntry,
        index: Int,
        item: ItemStack,
        amount: Int,
        price: Double,
        isBuy: Boolean,
    ) {
        val event = ShopTransactionEvent(
            player = player,
            shopId = definition.id,
            itemIndex = index,
            item = item.clone(),
            amount = amount,
            price = price,
            isBuy = isBuy,
            notificationWebhookId = definition.notificationWebhookId,
        )
        runCatching { Bukkit.getPluginManager().callEvent(event) }
            .onFailure { logger.warning("A ShopTransactionEvent listener failed: ${it.message}") }
    }

    private fun String.toComponent() = miniMessage.deserialize(this)
    private fun formatPrice(value: Double): String = String.format("%.2f", value)

    /** Refuses the transaction: tells the player why and plays the denied sound. */
    private fun refuse(player: Player, definition: ShopDefinitionEntry, message: String) {
        player.sendMessage(message.toComponent())
        playSound(player, definition.deniedSound)
    }

    /** "3 × Kelp, 12 × Copper Ingot" — what a refusal names when items are missing. */
    private fun describeMissing(missing: Map<String, Int>): String =
        missing.entries.joinToString(", ") { "${it.value} × ${it.key}" }

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

        // The sold item is built BEFORE payment: both payment paths need to know whether the
        // player can receive it, and a payment taken in front of a full inventory must be refunded
        // — or, for a barter, never happen at all.
        val item = cfg.item.get(player).build(player).clone()
        val totalAmount = amount
        val maxStack = item.maxStackSize

        val availableSpace = player.inventory.contents.filterNotNull().sumOf { slot ->
            if (slot.type == Material.AIR || slot.type.isAir) maxStack
            else if (slot.isSimilar(item)) maxStack - slot.amount
            else 0
        }
        val emptySlots = player.inventory.contents.count { it == null || it.type == Material.AIR || it.type.isAir }
        val totalAvailable = availableSpace + emptySlots * maxStack

        // A price paid in ITEMS does not go through `Economy`, and cannot: that abstraction deals
        // in a balance (`Double`), whereas a barter asks for "12 ingots and 3 kelp". Not an
        // implementation limit, a different shape. See `ItemPriceRule`.
        val itemPrice = itemPriceService.applicablePrice(definition, cfg)
        val taxedCost: Double

        if (itemPrice != null) {
            when (val verdict = itemPriceService.evaluate(player, itemPrice, amount)) {
                is ItemPriceService.Verdict.Invalid -> {
                    // Neither a sale nor a refusal addressed to the player: a configuration
                    // mistake. Log it, and above all do NOT deliver — an empty price is not a
                    // zero price.
                    logger.warning(
                        "Shop '${definition.id}', item ${index + 1}: unusable price in items " +
                            "(${verdict.reason}). The item is not sold."
                    )
                    return refuse(player, definition, definition.noEconomyMessage)
                }

                is ItemPriceService.Verdict.Missing -> {
                    return refuse(
                        player,
                        definition,
                        definition.cannotAffordItemsMessage
                            .replace("{missing}", describeMissing(verdict.missing))
                            .replace("{amount}", amount.toString()),
                    )
                }

                is ItemPriceService.Verdict.Payable -> {
                    // Space is checked BEFORE the withdrawal: a barter has no clean refund —
                    // giving items back needs room, precisely what is missing. Nothing is taken
                    // until delivery is possible.
                    if (totalAvailable < totalAmount) {
                        return refuse(
                            player,
                            definition,
                            definition.inventoryFullMessage.replace("{amount}", amount.toString())
                                .replace("{price}", "—"),
                        )
                    }
                    // Nothing between the evaluation and the withdrawal yields: the plan names
                    // slots by index, and an inventory changed in between would have something
                    // else removed.
                    itemPriceService.withdraw(player, verdict.withdrawals)
                    taxedCost = 0.0
                }
            }
        } else {
            val baseCost = calcBuyPrice(player, definition, cfg, amount)
            val promotedCost = promotionService.getPromotedPrice(baseCost, definition.id, index)
            taxedCost = taxService.applyBuyTax(promotedCost, definition, cfg)

            val economy = economyService.resolve(definition)
                ?: return refuse(player, definition, definition.noEconomyMessage)

            // Two gates on purpose. `canAfford` is the one that produces the player-facing refusal,
            // and `withdraw` re-checks internally so no economy adapter can ever settle a purchase
            // that leaves the player below zero.
            if (!economy.canAfford(player.uniqueId, taxedCost) ||
                !economy.withdraw(player.uniqueId, taxedCost)
            ) {
                return refuse(
                    player,
                    definition,
                    definition.cannotAffordMessage.replace("{amount}", amount.toString())
                        .replace("{price}", formatPrice(taxedCost)),
                )
            }

            if (totalAvailable < totalAmount) {
                // Refund
                economy.deposit(player.uniqueId, taxedCost)
                return refuse(
                    player,
                    definition,
                    definition.inventoryFullMessage.replace("{amount}", amount.toString())
                        .replace("{price}", formatPrice(taxedCost)),
                )
            }
        }

        // Give items
        item.amount = totalAmount
        player.inventory.addItem(item)

        // Record transaction
        stockService.buy(definition.id, index, amount, cfg.dynamicPricing, definition.stockOwnerFor(player.uniqueId))
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
        announce(player, definition, index, item, amount, taxedCost, isBuy = true)

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

        val baseReward = calcSellPrice(player, definition, cfg, amount)
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

        stockService.sell(definition.id, index, amount, cfg.dynamicPricing, definition.stockOwnerFor(player.uniqueId))

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
        announce(player, definition, index, template, amount, taxedReward, isBuy = false)

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
        if (!cfg.sellAllEnabled) return refuse(player, definition, definition.cannotSellMessage)
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

        val template = cfg.item.get(player).build(player).clone()
        template.amount = 1
        val maxStack = template.maxStackSize

        val itemPrice = itemPriceService.applicablePrice(definition, cfg)
        val taxRate = if (definition.taxRate > 0) (1.0 + definition.taxRate / 100.0) else 1.0
        val unitPrice: Double
        val maxByBalance: Int

        if (itemPrice != null) {
            unitPrice = 0.0
            // The ceiling is what an inventory can hold anyway: searching beyond it would make
            // the bisection work on quantities no pocket could receive.
            maxByBalance = itemPriceService.maxAffordableUnits(
                player,
                itemPrice,
                ceiling = player.inventory.contents.size * maxStack,
            )
        } else {
            val economy = economyService.resolve(definition) ?: return
            unitPrice = when (cfg.priceMode) {
                PriceMode.FIXED -> cfg.fixedBuyPrice
                PriceMode.DYNAMIC -> {
                    val stock = stockOf(player, definition, index, cfg)
                    priceService.calculateBuyPrice(stock, cfg.dynamicPricing)
                }
                // Unreachable: `applicablePrice` already claimed ITEMS. The branch exists so the
                // `when` stays exhaustive and a future mode cannot slip through unnoticed.
                PriceMode.ITEMS -> 0.0
            }

            if (unitPrice <= 0.0) return

            // An unreadable balance buys nothing: `handleBuy` would refuse anyway, but reporting it
            // here keeps "buy max" from silently doing nothing at all.
            val balance = economy.balanceOrNull(player.uniqueId)
                ?: return refuse(player, definition, definition.cannotAffordMessage)
            maxByBalance = (balance / (unitPrice * taxRate)).toInt()
        }

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
                if (maxByBalance <= 0) {
                    // On a barter, "you cannot afford this" says nothing: the list of missing
                    // items is what informs. The refusal borrows the items message.
                    if (itemPrice != null) {
                        val missing = (itemPriceService.evaluate(player, itemPrice, 1)
                            as? ItemPriceService.Verdict.Missing)
                            ?.missing
                            ?.let(::describeMissing)
                            ?: "—"
                        definition.cannotAffordItemsMessage.replace("{missing}", missing)
                    } else {
                        definition.cannotAffordMessage
                    }
                } else if (maxByInventory <= 0) definition.inventoryFullMessage
                else definition.limitReachedMessage
            return refuse(
                player,
                definition,
                message.replace("{amount}", "1")
                    .replace("{price}", formatPrice(unitPrice * taxRate)),
            )
        }
        handleBuy(player, cfg, definition, maxAmount)
    }

    /** The stock [player] trades against: their own in a per-player shop, the shared one otherwise. */
    private fun stockOf(player: Player, definition: ShopDefinitionEntry, index: Int, cfg: ShopItemConfig): Int =
        stockService.getStock(definition.id, index, cfg.dynamicPricing.stockMax, definition.stockOwnerFor(player.uniqueId))

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

    private fun calcBuyPrice(player: Player, definition: ShopDefinitionEntry, cfg: ShopItemConfig, amount: Int): Double {
        return when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedBuyPrice * amount
            PriceMode.DYNAMIC -> {
                val index = definition.items.indexOf(cfg)
                val stock = stockOf(player, definition, index, cfg)
                priceService.calculateBuyPrice(stock, cfg.dynamicPricing) * amount
            }
            // A barter's scalar price is zero by construction: payment is settled in items before
            // this value serves anything. It only survives for the transaction log.
            PriceMode.ITEMS -> 0.0
        }
    }

    private fun calcSellPrice(player: Player, definition: ShopDefinitionEntry, cfg: ShopItemConfig, amount: Int): Double {
        return when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedSellPrice * amount
            PriceMode.DYNAMIC -> {
                val index = definition.items.indexOf(cfg)
                val stock = stockOf(player, definition, index, cfg)
                priceService.calculateSellPrice(stock, cfg.dynamicPricing) * amount
            }
            // Zero makes `handleSell` refuse, and that is the intended effect: an item bought with
            // resources is not sold back for coins.
            PriceMode.ITEMS -> 0.0
        }
    }

    private fun playSound(player: Player, sound: Sound?) {
        if (sound == null) return
        val ctx = context {}
        sound.play(player, ctx)
    }
}
