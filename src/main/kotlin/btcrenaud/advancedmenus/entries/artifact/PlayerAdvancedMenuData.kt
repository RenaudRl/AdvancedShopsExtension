package btcrenaud.advancedmenus.entries.artifact

import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.books.pages.Colors

/**
 * Artifact entry that stores persistent menu data for a player.
 */
@Entry("player_advanced_menu_data", "Player Advanced Menu Data", Colors.PURPLE, "mdi:account-cog")
class PlayerAdvancedMenuData(
    @Help("Persistent FOV value for this player (30-110)")
    var personalFov: Double = 70.0,
    
    override val id: String = "",
    override val name: String = ""
) : ArtifactEntry {
    override val artifactId: String get() = id
}
