package com.btc.shops

import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.service.ShopGuiService
import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.TypewriterCommand
import com.typewritermc.engine.paper.command.dsl.*
import com.typewritermc.engine.paper.utils.sendMini
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import org.koin.java.KoinJavaComponent
import java.util.concurrent.CompletableFuture

/** Shop ids, with tab completion over every defined shop. */
class ShopArgumentType : CustomArgumentType.Converted<String, String> {
    override fun convert(nativeType: String): String {
        Query.findById<ShopDefinitionEntry>(nativeType)
            ?: throw SimpleCommandExceptionType(LiteralMessage("Unknown shop: $nativeType")).create()
        return nativeType
    }

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val input = builder.remaining.lowercase()
        Query(ShopDefinitionEntry::class).find()
            .filter { it.name.lowercase().startsWith(input) || it.id.lowercase().startsWith(input) }
            .forEach { builder.suggest(it.id) }
        return builder.buildFuture()
    }
}

/**
 * `/typewriter shop` commands.
 * - `/tw shop <shop>` → opens that shop, with tab completion over the defined shops.
 */
@TypewriterCommand
fun CommandTree.shopCommands() = literal("shop") {
    literal("admin") { buildShopAdminTree() }

    executes {
        sender.sendMini(
            "<gold>Shop commands:</gold> <yellow>/tw shop <id></yellow> to open a shop, " +
                "<yellow>/tw shop admin</yellow> for operator tools."
        )
    }

    argument("shop", ShopArgumentType(), String::class) { shopArg ->
        withPermission(ShopPermissions.OPEN)
        executePlayer { player ->
            val definition = Query.findById<ShopDefinitionEntry>(shopArg()) ?: return@executePlayer
            KoinJavaComponent.get<ShopGuiService>(ShopGuiService::class.java)
                .open(player, definition, delayTicks = 0L)
        }
    }
}
