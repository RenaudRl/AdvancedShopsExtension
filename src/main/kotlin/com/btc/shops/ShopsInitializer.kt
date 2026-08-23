package com.btc.shops

import com.btc.shops.service.ShopButtonActionHandler
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import org.bukkit.Bukkit
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
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

        registerPermissions()

        logger.info("[Shops] Extension initialized")
    }

    /**
     * Registers the player-facing shop command permission.
     * Typewriter extensions do not provide a plugin.yml, so an unregistered
     * Bukkit permission defaults to operators only.
     */
    private fun registerPermissions() {
        mapOf(
            ShopPermissions.OPEN to PermissionDefault.TRUE,
            ShopPermissions.ADMIN to PermissionDefault.OP,
        ).forEach { (permission, default) ->
            if (Bukkit.getPluginManager().getPermission(permission) == null) {
                Bukkit.getPluginManager().addPermission(Permission(permission, default))
            }
        }
    }

    override suspend fun shutdown() {
        KoinJavaComponent.get<ShopButtonActionHandler>(ShopButtonActionHandler::class.java).unregister()
        logger.info("[Shops] Extension shutting down")
    }
}
