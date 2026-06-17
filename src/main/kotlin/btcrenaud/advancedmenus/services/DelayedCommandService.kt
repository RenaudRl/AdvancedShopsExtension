package btcrenaud.advancedmenus.services

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import com.typewritermc.engine.paper.TypewriterPaperPlugin

class DelayedCommandService {
    private val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)

    /**
     * Executes a command after a delay, ensuring player is still online.
     * Uses Folia-safe region scheduler.
     */
    fun executeDelayed(player: Player, command: String, delayTicks: Long) {
        if (delayTicks <= 0) {
            player.performCommand(command)
            return
        }

        player.scheduler.runDelayed(plugin, { _ ->
            if (player.isOnline) {
                player.performCommand(command)
            }
        }, null, delayTicks)
    }
}
