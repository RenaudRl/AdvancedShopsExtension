package com.btc.shops

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.typewritermc.core.entries.Query
import com.typewritermc.engine.paper.command.dsl.*
import com.typewritermc.engine.paper.utils.sendMini
import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.manifest.ShopItemConfig
import com.btc.shops.service.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.koin.java.KoinJavaComponent
import java.time.Instant
import java.time.format.DateTimeFormatter

internal fun CommandTree.buildShopAdminTree() {
    withPermission(ShopPermissions.ADMIN)

    executes { sendShopAdminHelp(sender) }

    literal("list") {
        executes {
            val shops = Query(ShopDefinitionEntry::class).find().toList().sortedBy { it.id.lowercase() }
            if (shops.isEmpty()) {
                sender.sendShopMessage("No shop definitions are currently loaded.", NamedTextColor.YELLOW)
                return@executes
            }

            sender.sendShopMessage("Loaded shops (${shops.size}):", NamedTextColor.GOLD)
            shops.forEach { shop ->
                sender.sendShopMessage(
                    "- ${shop.id} | ${shop.name} | items=${shop.items.size} | pools=${shop.itemPools.size}",
                    NamedTextColor.WHITE,
                )
            }
        }
    }

    literal("info") {
        argument("shop", ShopArgumentType(), String::class) { shopRef ->
            executes {
                findShop(sender, shopRef())?.let { sendShopInfo(sender, it) }
            }
        }
    }

    literal("open") {
        argument("shop", ShopArgumentType(), String::class) { shopRef ->
            executePlayerOrTarget { target ->
                findShop(sender, shopRef())?.let { definition ->
                    KoinJavaComponent.get<ShopGuiService>(ShopGuiService::class.java)
                        .open(target, definition)
                    sender.sendShopMessage("Opened ${definition.id} for ${target.name}.", NamedTextColor.GREEN)
                }
            }
        }
    }

    literal("refresh") {
        executePlayerOrTarget { target ->
            KoinJavaComponent.get<ShopGuiService>(ShopGuiService::class.java).refresh(target)
            sender.sendShopMessage("Refreshed the shop session for ${target.name}.", NamedTextColor.GREEN)
        }
    }

    literal("close") {
        executePlayerOrTarget { target ->
            KoinJavaComponent.get<ShopGuiService>(ShopGuiService::class.java).close(target)
            sender.sendShopMessage("Closed the shop session for ${target.name}.", NamedTextColor.GREEN)
        }
    }

    literal("reset") {
        argument("shop", ShopArgumentType(), String::class) { shopRef ->
            executes {
                findShop(sender, shopRef())?.let { definition ->
                    KoinJavaComponent.get<ResetService>(ResetService::class.java).resetNow(definition)
                    sender.sendShopMessage("Reset runtime stock and limits for ${definition.id}.", NamedTextColor.GREEN)
                }
            }
        }
    }

    literal("stock") {
        argument("shop", ShopArgumentType(), String::class) { shopRef ->
            argument("item", IntegerArgumentType.integer(0), Int::class) { itemRef ->
                literal("get") {
                    executes {
                        findShop(sender, shopRef())?.let { definition ->
                            showStock(sender, definition, itemRef())
                        }
                    }
                }
                literal("set") {
                    argument("amount", IntegerArgumentType.integer(0), Int::class) { amountRef ->
                        executes {
                            findShop(sender, shopRef())?.let { definition ->
                                mutateStock(sender, definition, itemRef(), StockMutation.SET, amountRef())
                            }
                        }
                    }
                }
                literal("add") {
                    argument("amount", IntegerArgumentType.integer(1), Int::class) { amountRef ->
                        executes {
                            findShop(sender, shopRef())?.let { definition ->
                                mutateStock(sender, definition, itemRef(), StockMutation.ADD, amountRef())
                            }
                        }
                    }
                }
                literal("remove") {
                    argument("amount", IntegerArgumentType.integer(1), Int::class) { amountRef ->
                        executes {
                            findShop(sender, shopRef())?.let { definition ->
                                mutateStock(sender, definition, itemRef(), StockMutation.REMOVE, amountRef())
                            }
                        }
                    }
                }
            }
        }
    }

    literal("history") {
        executePlayerOrTarget { target ->
            val history = KoinJavaComponent.get<TransactionLogService>(TransactionLogService::class.java)
                .getHistory(target.uniqueId)
                .take(10)
            sender.sendShopMessage("Recent transactions for ${target.name} (${history.size}):", NamedTextColor.GOLD)
            if (history.isEmpty()) {
                sender.sendShopMessage("No transactions recorded in the current runtime.", NamedTextColor.YELLOW)
            } else {
                history.forEach { record ->
                    val time = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(record.timestamp))
                    sender.sendShopMessage(
                        "$time | ${record.type} | ${record.shopId}[$record.itemIndex] | " +
                            "amount=${record.amount} | price=${record.price}",
                        NamedTextColor.WHITE,
                    )
                }
            }
        }
    }
}

private enum class StockMutation {
    SET,
    ADD,
    REMOVE,
}

private fun sendShopAdminHelp(sender: CommandSender) {
    sender.sendMini(
        """
        <gold>Shop admin commands:</gold>
        <yellow>/tw shop admin list</yellow> <gray>List loaded shops.</gray>
        <yellow>/tw shop admin info <shop></yellow> <gray>Inspect a definition.</gray>
        <yellow>/tw shop admin open <shop> [target]</yellow> <gray>Open a shop for a player.</gray>
        <yellow>/tw shop admin refresh|close [target]</yellow> <gray>Manage an open session.</gray>
        <yellow>/tw shop admin reset <shop></yellow> <gray>Reset runtime stock and limits.</gray>
        <yellow>/tw shop admin stock <shop> <item> get|set|add|remove [amount]</yellow>
        <yellow>/tw shop admin history [target]</yellow> <gray>Show recent transactions.</gray>
        """.trimIndent()
    )
}

private fun findShop(sender: CommandSender, id: String): ShopDefinitionEntry? {
    val definition = Query.findById<ShopDefinitionEntry>(id)
    if (definition == null) {
        sender.sendShopMessage("Unknown shop: $id", NamedTextColor.RED)
    }
    return definition
}

private fun sendShopInfo(sender: CommandSender, definition: ShopDefinitionEntry) {
    sender.sendShopMessage("=== Shop ${definition.id} ===", NamedTextColor.GOLD)
    sender.sendShopMessage("Name: ${definition.name}")
    sender.sendShopMessage("Title: ${definition.title}")
    sender.sendShopMessage("Currency: ${definition.currency} | rows=${definition.rows}")
    sender.sendShopMessage(
        "Items: ${definition.items.size} direct | pools=${definition.itemPools.size} | " +
            "access criteria=${definition.criteria.size}",
    )
    sender.sendShopMessage("Reset policy: ${definition.reset::class.simpleName}")
    definition.items.forEachIndexed { index, item ->
        sender.sendShopMessage(
            "[$index] ${item.name.ifBlank { "<unnamed>" }} | " +
                "buy=${item.buyEnabled} sell=${item.sellEnabled} | " +
                "playerLimit=${item.playerLimit} globalLimit=${item.globalLimit} | " +
                "item criteria=${item.criteria.size}",
        )
    }
}

private fun showStock(sender: CommandSender, definition: ShopDefinitionEntry, itemIndex: Int) {
    val item = directItem(sender, definition, itemIndex) ?: return
    val max = item.dynamicPricing.stockMax
    if (max <= 0) {
        sender.sendShopMessage("${definition.id}[$itemIndex] uses unlimited stock.", NamedTextColor.YELLOW)
        return
    }

    val stock = KoinJavaComponent.get<StockService>(StockService::class.java)
        .getStock(definition.id, itemIndex, max)
    sender.sendShopMessage("${definition.id}[$itemIndex] stock: $stock/$max.", NamedTextColor.GREEN)
}

private fun mutateStock(
    sender: CommandSender,
    definition: ShopDefinitionEntry,
    itemIndex: Int,
    mutation: StockMutation,
    amount: Int,
) {
    val item = directItem(sender, definition, itemIndex) ?: return
    val max = item.dynamicPricing.stockMax
    if (max <= 0) {
        sender.sendShopMessage("${definition.id}[$itemIndex] uses unlimited stock.", NamedTextColor.YELLOW)
        return
    }

    val stockService = KoinJavaComponent.get<StockService>(StockService::class.java)
    val current = stockService.getStock(definition.id, itemIndex, max)
    val updated = when (mutation) {
        StockMutation.SET -> amount
        StockMutation.ADD -> (current + amount).coerceAtMost(max)
        StockMutation.REMOVE -> (current - amount).coerceAtLeast(0)
    }
    if (updated !in 0..max) {
        sender.sendShopMessage("Stock must stay between 0 and $max.", NamedTextColor.RED)
        return
    }

    stockService.reset(definition.id, itemIndex, updated)
    sender.sendShopMessage("${definition.id}[$itemIndex] stock set to $updated/$max.", NamedTextColor.GREEN)
}

private fun directItem(
    sender: CommandSender,
    definition: ShopDefinitionEntry,
    itemIndex: Int,
): ShopItemConfig? {
    val item = definition.items.getOrNull(itemIndex)
    if (item == null) {
        sender.sendShopMessage("Unknown direct item index $itemIndex in ${definition.id}.", NamedTextColor.RED)
    }
    return item
}

private fun CommandSender.sendShopMessage(text: String, color: NamedTextColor = NamedTextColor.WHITE) {
    sendMessage(Component.text(text, color))
}
