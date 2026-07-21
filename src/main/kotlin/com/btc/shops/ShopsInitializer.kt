package com.btc.shops

import com.btc.shops.service.ShopButtonActionHandler
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

@Singleton
object ShopsInitializer : Initializable {
    private val logger = LoggerFactory.getLogger(ShopsInitializer::class.java)

    override suspend fun initialize() {
        // Shop buttons are routed in-process by the GUI extension (see ShopButtonActionHandler).
        // They used to be registered as a real `/typewritershop` Bukkit command and bounced through
        // the console sender, which exposed an internal command to players and tab completion.
        // The player-facing entry point is `/tw shop` (see ShopCommands).
        KoinJavaComponent.get<ShopButtonActionHandler>(ShopButtonActionHandler::class.java).register()

        logger.info("[Shops] Extension initialized")
    }

    override suspend fun shutdown() {
        KoinJavaComponent.get<ShopButtonActionHandler>(ShopButtonActionHandler::class.java).unregister()
        logger.info("[Shops] Extension shutting down")
    }
}
