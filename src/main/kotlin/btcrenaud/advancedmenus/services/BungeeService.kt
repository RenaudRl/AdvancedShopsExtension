package btcrenaud.advancedmenus.services

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import com.typewritermc.engine.paper.TypewriterPaperPlugin
import com.google.common.io.ByteStreams

class BungeeService {
    private val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)

    init {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord")
    }

    /**
     * Sends a player to a specific server.
     * Supports syntax like [server] <target> or just server name.
     */
    fun connect(player: Player, serverName: String) {
        val out = ByteStreams.newDataOutput()
        out.writeUTF("Connect")
        out.writeUTF(serverName)
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray())
    }
}
