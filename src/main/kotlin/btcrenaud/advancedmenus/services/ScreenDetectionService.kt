package btcrenaud.advancedmenus.services

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings
import com.typewritermc.engine.paper.TypewriterPaperPlugin
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects player screen/rendering settings from client packets.
 *
 * Captures FOV-related data from CLIENT_SETTINGS packets. While Minecraft
 * does not transmit the actual FOV value, the GUI scale setting helps
 * adjust menu element sizes for different display configurations.
 *
 * For FOV adaptation, we use a heuristic default of 70° (Minecraft standard)
 * and provide a calibration mechanism that measures angular range from
 * player look behavior during menu setup.
 */
class ScreenDetectionService {

    /**
     * Per-player detected screen settings.
     *
     * @property guiScale 0 = auto, 1-4 = explicit scale
     * @property estimatedFov Estimated FOV in degrees (default 70.0 - Vanilla standard)

     * @property viewDistance Client render distance (chunks)
     */
    data class PlayerScreenInfo(
        val guiScale: Int = 0,
        val estimatedFov: Double = 70.0,

        val viewDistance: Int = 12,
        val language: String = "en_us"
    )

    private val playerInfo = ConcurrentHashMap<UUID, PlayerScreenInfo>()
    private var packetListener: PacketListenerAbstract? = null
    
    // Persistent Storage Key
    private val fovKey by lazy {
        val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)
        NamespacedKey(plugin, "player_fov")
    }

    /**
     * Starts listening for client settings packets.
     * Must be called once during initialization.
     */
    fun start() {
        if (packetListener != null) return
        val listener = object : PacketListenerAbstract() {
            override fun onPacketReceive(event: PacketReceiveEvent) {
                try {
                    if (event.packetType != PacketType.Play.Client.CLIENT_SETTINGS) return
                    val uuid = event.user?.uuid ?: return

                    val wrapper = WrapperPlayClientSettings(event)
                    val current = playerInfo[uuid] ?: PlayerScreenInfo()
                    playerInfo[uuid] = current.copy(
                        guiScale = wrapper.viewDistance.coerceIn(0, 4), // GUI scale 0=auto, 1-4
                        viewDistance = wrapper.viewDistance,
                        language = wrapper.locale ?: current.language
                    )
                } catch (_: Exception) {
                    // Ignore malformed packets
                }
            }
        }
        packetListener = listener
        PacketEvents.getAPI().eventManager.registerListener(listener)
    }

    /**
     * Stops listening and clears data.
     */
    fun shutdown() {
        packetListener?.let {
            PacketEvents.getAPI().eventManager.unregisterListener(it)
        }
        packetListener = null
        playerInfo.clear()
    }

    /**
     * Returns the player's estimated FOV.
     * Currently returns default 70° since Minecraft CLIENT_SETTINGS
     * doesn't transmit the actual FOV. The adaptiveScale system
     * compensates by adjusting the forward distance based on
     * the designer's reference FOV.
     */
    fun getPlayerFov(player: Player): Double {
        val uuid = player.uniqueId
        val cached = playerInfo[uuid]?.estimatedFov
        if (cached != null) return cached

        // Fallback to Persistence (PDC)
        val pdc = player.persistentDataContainer
        if (pdc.has(fovKey, PersistentDataType.DOUBLE)) {
            val savedFov = pdc.get(fovKey, PersistentDataType.DOUBLE) ?: 70.0
            setEstimatedFov(player, savedFov, saveToPdc = false) // Cache it
            return savedFov
        }

        return 70.0
    }

    /**
     * Returns the player's GUI scale setting.
     * 0 = auto, 1-4 = explicit scale.
     */
    fun getGuiScale(player: Player): Int {
        return playerInfo[player.uniqueId]?.guiScale ?: 0
    }

    /**
     * Returns the player's view distance.
     */
    fun getViewDistance(player: Player): Int {
        return playerInfo[player.uniqueId]?.viewDistance ?: 12
    }

    /**
     * Sets an estimated FOV for a player (e.g. from a calibration flow).
     */
    fun setEstimatedFov(player: Player, fov: Double, saveToPdc: Boolean = true) {
        val current = playerInfo[player.uniqueId] ?: PlayerScreenInfo()
        val clampedFov = fov.coerceIn(30.0, 120.0)
        playerInfo[player.uniqueId] = current.copy(estimatedFov = clampedFov)
        
        if (saveToPdc) {
            try {
                player.persistentDataContainer.set(fovKey, PersistentDataType.DOUBLE, clampedFov)
            } catch (e: Exception) {
                // Fail-safe for invalid PDC access
            }
        }
    }

    /**
     * Clears data for a player (on disconnect).
     */
    fun removePlayer(uuid: UUID) {
        playerInfo.remove(uuid)
    }
}
