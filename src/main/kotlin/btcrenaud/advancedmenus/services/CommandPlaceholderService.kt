package btcrenaud.advancedmenus.services

import com.typewritermc.engine.paper.extensions.placeholderapi.PlaceholderHandler
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import com.typewritermc.engine.paper.TypewriterPaperPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import btcrenaud.advancedmenus.util.CameraBasis

/**
 * Handles PlaceholderAPI placeholders and the /advancedmenus command.
 *
 * Placeholders:
 *   %advancedmenus_cursor_x%       — Current cursor X position
 *   %advancedmenus_cursor_y%       — Current cursor Y position
 *   %advancedmenus_active_menu%    — Active menu ID or "none"
 *   %advancedmenus_button_count%   — Number of buttons in active menu
 *   %advancedmenus_button_N_x%     — X position of button N (1-indexed)
 *   %advancedmenus_button_N_y%     — Y position of button N
 *   %advancedmenus_button_N_name%  — Name of button N
 *   %advancedmenus_button_N_hovered% — Whether button N is hovered
 */
class CommandPlaceholderService : PlaceholderHandler, CommandExecutor, KoinComponent {

    private val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)
    private val sessionService: MenuSessionService by inject()
    private val screenService: ScreenDetectionService by inject()

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        if (player == null) return null

        val session = sessionService.getSession(player)
        val pLower = params.lowercase()
        return when {
            pLower == "cursor_x" -> session?.cursorX?.toString() ?: "0"
            pLower == "cursor_y" -> session?.cursorY?.toString() ?: "0"
            pLower == "active_menu" -> session?.menuId ?: "none"
            pLower == "button_count" -> session?.buttons?.size?.toString() ?: "0"
            pLower == "can_scroll_down" -> session?.let { (it.targetScrollY > it.wheelConfig.minBoundary).toString() } ?: "false"
            pLower == "can_scroll_up" -> session?.let { (it.targetScrollY < it.wheelConfig.maxBoundary).toString() } ?: "false"
            pLower == "can_zoom_in" -> session?.let { (it.targetZoomLevel > it.wheelConfig.minBoundary).toString() } ?: "false"
            pLower == "can_zoom_out" -> session?.let { (it.targetZoomLevel < it.wheelConfig.maxBoundary).toString() } ?: "false"
            pLower.startsWith("button_") -> {
                val parts = pLower.split("_")
                val index = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                val property = parts.getOrNull(2) ?: return null

                val button = session?.buttons?.getOrNull(index) ?: return null
                when (property) {
                    "x" -> button.screenX.toString()
                    "y" -> button.screenY.toString()
                    "name" -> button.name
                    "hovered" -> button.hovered.toString()
                    else -> null
                }
            }
            else -> null
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§b§lAdvancedMenus §8| §fVersion §e1.0 §fby §6Renaud")
            sender.sendMessage("§7- /advancedmenus fov <val> §8| §fSync your FOV (ex: 90)")
            return true
        }

        when (args[0].lowercase()) {
            "fov" -> {
                if (sender !is Player) return true
                val fov = args.getOrNull(1)?.toDoubleOrNull() ?: run {
                    sender.sendMessage("§cUsage: /advancedmenus fov <value>")
                    return true
                }
                
                screenService.setEstimatedFov(sender, fov)
                
                // Real-time refresh for active session
                sessionService.getSession(sender)?.let { session ->
                    session.playerFov = fov
                    // Recalculate forwardDistance from the original base distance
                    session.forwardDistance = CameraBasis.adjustDistanceForFov(session.baseForwardDistance, actualFov = fov)
                    session.basis = CameraBasis(session.cameraLocation.yaw, session.cameraLocation.pitch)
                    sender.sendMessage("§a[AdvancedMenus] FOV synced to $fov°. Session updated.")
                } ?: run {
                    sender.sendMessage("§a[AdvancedMenus] FOV synced to $fov°. (Persistent)")
                }
            }
        }
        return true
    }
}
