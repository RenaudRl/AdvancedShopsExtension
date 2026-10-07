package com.btc.shops

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.typewritermc.core.entries.Query
import com.typewritermc.engine.paper.command.dsl.*
import com.typewritermc.engine.paper.utils.sendMini
import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.manifest.ShopItemConfig
import com.btc.shops.service.*
import org.bukkit.command.CommandSender
import org.koin.java.KoinJavaComponent
import java.time.Instant
import java.time.format.DateTimeFormatter

// Every reply below is a snippet of [ShopTexts]: staff read the same text an admin edits in the snippets file.

internal fun CommandTree.buildShopAdminTree() {
    withPermission(ShopPermissions.ADMIN)

    executes { sender.sendMini(ShopTexts.adminHelp()) }

    literal("list") {
        executes {
            val shops = Query(ShopDefinitionEntry::class).find().toList().sortedBy { it.id.lowercase() }
            if (shops.isEmpty()) {
                sender.sendMini(ShopTexts.listEmpty())
                return@executes
            }

            sender.sendMini(ShopTexts.listHeader(shops.size))
            shops.forEach { shop ->
                sender.sendMini(ShopTexts.listLine(shop.id, shop.name, shop.items.size, shop.itemPools.size))
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
                    sender.sendMini(ShopTexts.opened(definition.id, target.name))
                }
            }
        }
    }

    literal("refresh") {
        executePlayerOrTarget { target ->
            KoinJavaComponent.get<ShopGuiService>(ShopGuiService::class.java).refresh(target)
            sender.sendMini(ShopTexts.refreshed(target.name))
        }
    }

    literal("close") {
        executePlayerOrTarget { target ->
            KoinJavaComponent.get<ShopGuiService>(ShopGuiService::class.java).close(target)
            sender.sendMini(ShopTexts.closed(target.name))
        }
    }

    literal("reset") {
        argument("shop", ShopArgumentType(), String::class) { shopRef ->
            executes {
                findShop(sender, shopRef())?.let { definition ->
                    KoinJavaComponent.get<ResetService>(ResetService::class.java).resetNow(definition)
                    sender.sendMini(ShopTexts.reset(definition.id))
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
            sender.sendMini(ShopTexts.historyHeader(target.name, history.size))
            if (history.isEmpty()) {
                sender.sendMini(ShopTexts.historyEmpty())
            } else {
                history.forEach { record ->
                    val time = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(record.timestamp))
                    sender.sendMini(
                        ShopTexts.historyLine(time, record.type, record.shopId, record.itemIndex, record.amount, record.price),
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

private fun findShop(sender: CommandSender, id: String): ShopDefinitionEntry? {
    val definition = Query.findById<ShopDefinitionEntry>(id)
    if (definition == null) {
        sender.sendMini(ShopTexts.unknownShop(id))
    }
    return definition
}

private fun sendShopInfo(sender: CommandSender, definition: ShopDefinitionEntry) {
    sender.sendMini(ShopTexts.infoTitle(definition.id))
    sender.sendMini(ShopTexts.infoName(definition.name))
    sender.sendMini(ShopTexts.infoInventoryTitle(definition.title))
    sender.sendMini(ShopTexts.infoCurrency(definition.currency.toString(), definition.rows))
    sender.sendMini(ShopTexts.infoItems(definition.items.size, definition.itemPools.size, definition.criteria.size))
    sender.sendMini(ShopTexts.infoReset(definition.reset::class.simpleName.toString()))
    definition.items.forEachIndexed { index, item ->
        sender.sendMini(
            ShopTexts.infoItem(
                index = index,
                name = item.name,
                buy = item.buyEnabled,
                sell = item.sellEnabled,
                playerLimit = item.playerLimit,
                globalLimit = item.globalLimit,
                criteria = item.criteria.size,
            ),
        )
    }
}

private fun showStock(sender: CommandSender, definition: ShopDefinitionEntry, itemIndex: Int) {
    val item = directItem(sender, definition, itemIndex) ?: return
    val max = item.dynamicPricing.stockMax
    if (max <= 0) {
        sender.sendMini(ShopTexts.stockUnlimited(definition.id, itemIndex))
        return
    }

    val stock = KoinJavaComponent.get<StockService>(StockService::class.java)
        .getStock(definition.id, itemIndex, max)
    sender.sendMini(ShopTexts.stockValue(definition.id, itemIndex, stock, max))
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
        sender.sendMini(ShopTexts.stockUnlimited(definition.id, itemIndex))
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
        sender.sendMini(ShopTexts.stockOutOfRange(max))
        return
    }

    stockService.reset(definition.id, itemIndex, updated)
    sender.sendMini(ShopTexts.stockUpdated(definition.id, itemIndex, updated, max))
}

private fun directItem(
    sender: CommandSender,
    definition: ShopDefinitionEntry,
    itemIndex: Int,
): ShopItemConfig? {
    val item = definition.items.getOrNull(itemIndex)
    if (item == null) {
        sender.sendMini(ShopTexts.unknownItem(definition.id, itemIndex))
    }
    return item
}
