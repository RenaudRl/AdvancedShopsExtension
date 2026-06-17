package btcrenaud.advancedmenus.entries.manifest

import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.engine.paper.utils.Sound
import org.bukkit.entity.Player
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders

/**
 * Global configuration manifest for the AdvancedMenus extension.
 *
 * Provides extension-wide defaults for cursor behavior, sounds, and debug settings.
 * Only one instance should exist per Typewriter configuration.
 */
@Entry("advanced_menus_config", "Advanced Menus Config", Colors.GRAY, "mdi:cog")
class ExtensionConfigManifestEntry(
    @Help("Enable debug logging") val debug: Boolean = false,
    @Help("Default BetterHUD popup if none specified") val defaultPopup: String = "",
    @Help("Default invisibility state for camera sessions")
    val defaultInvisibility: Boolean = true,

    @Help("Default cursor sensitivity (1.0 = normal)")
    val defaultSensitivity: Double = 1.0,
    @Help("Default cursor smoothing factor (0.3 = smooth, 1.0 = instant)")
    val defaultSmoothing: Double = 1.0,
    @Help("Default sound played when hovering over a button")
    val defaultHoverSound: Sound = Sound.EMPTY,
    @Help("Default sound played when clicking a button")
    val defaultClickSound: Sound = Sound.EMPTY,
    @Help("Default hover animation duration in ticks")
    val defaultHoverDuration: Int = 3,
    @Help("Default global brightness for blocks (0-15)")
    val defaultBrightnessBlock: Int = 15,
    @Help("Default global brightness for sky (0-15)")
    val defaultBrightnessSky: Int = 15,
    @Help("Default cursor material if none specified")
    val defaultCursorMaterial: org.bukkit.Material = org.bukkit.Material.AIR,
    @Help("Default cursor custom model data")
    val defaultCursorModel: Int = 0,

    @Help("Global usage conditions (reusable configurations)")
    val globalConditions: Map<String, GlobalCondition> = emptyMap(),

    @Help("Actions to execute globally when a menu session starts")
    val globalOpenActions: List<com.typewritermc.core.entries.Ref<com.typewritermc.engine.paper.entry.entries.ActionEntry>> = emptyList(),
    @Help("Actions to execute globally when a menu session ends")
    val globalCloseActions: List<com.typewritermc.core.entries.Ref<com.typewritermc.engine.paper.entry.entries.ActionEntry>> = emptyList(),

    override val id: String = "",
    override val name: String = ""
) : ManifestEntry {

    companion object {
        private var instance: ExtensionConfigManifestEntry? = null

        fun getInstance(): ExtensionConfigManifestEntry? = instance

        fun checkCondition(player: Player, conditionName: String): Boolean {
            val config = instance ?: return true
            val condition = config.globalConditions[conditionName] ?: return true

            if (condition.permission.isNotEmpty() && !player.hasPermission(condition.permission)) return false

            if (condition.placeholder.isNotEmpty() && condition.placeholder.contains(":")) {
                val split = condition.placeholder.split(":", limit = 2)
                val actualValue = "%${split[0]}%".parsePlaceholders(player)
                if (actualValue != split[1]) return false
            }

            if (condition.worlds.isNotEmpty() && !condition.worlds.split(",").contains(player.world.name)) return false

            return true
        }
    }

    fun initialize() {
        instance = this
    }
}

/**
 * Data class for global reusable conditions.
 */
data class GlobalCondition(
    val permission: String = "",
    val placeholder: String = "",
    val worlds: String = "",
    val gamemodes: String = ""
)
