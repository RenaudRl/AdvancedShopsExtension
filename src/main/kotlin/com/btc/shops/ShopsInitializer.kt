package com.btc.shops

import com.btc.shops.service.ShopCommandExecutor
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.plugin
import org.bukkit.Bukkit
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

@Singleton
object ShopsInitializer : Initializable {
    private val logger = LoggerFactory.getLogger(ShopsInitializer::class.java)

    override suspend fun initialize() {
        val paperPlugin = plugin

        val executor = KoinJavaComponent.get<ShopCommandExecutor>(ShopCommandExecutor::class.java)

        // Register command via command map (same pattern as Crate/Dungeon extensions)
        runCatching {
            val commandMap = Bukkit.getCommandMap()
            val cmd = object : org.bukkit.command.Command(ShopCommandExecutor.COMMAND) {
                override fun execute(sender: org.bukkit.command.CommandSender, commandLabel: String, args: Array<out String>): Boolean {
                    return executor.onCommand(sender, this, commandLabel, args)
                }
            }
            commandMap.register(paperPlugin.name.lowercase(), cmd)
            logger.info("[Shops] Command /${ShopCommandExecutor.COMMAND} registered")
        }.onFailure { e ->
            logger.error("[Shops] Failed to register /${ShopCommandExecutor.COMMAND} command: ${e.message}")
        }

        logger.info("[Shops] Extension initialized")
    }

    override suspend fun shutdown() {
        logger.info("[Shops] Extension shutting down")
    }
}
