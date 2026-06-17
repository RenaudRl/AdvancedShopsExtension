package btcrenaud.advancedmenus.entries.action

import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.ActionEntry
import com.typewritermc.engine.paper.entry.entries.ActionTrigger
import com.typewritermc.engine.paper.utils.Sound
import btcrenaud.advancedmenus.services.MenuSessionService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Action entry that closes a currently active advanced menu for the player.
 * Restores game mode, visibility, camera, and optionally plays a close sound.
 */
@Entry("close_advanced_menu", "Close Advanced Menu", Colors.RED, "mdi:close-box")
class CloseAdvancedMenuActionEntry(
    @Help("Sound to play when menu is closed")
    val closeSound: Sound = Sound.EMPTY,

    override val id: String = "",
    override val name: String = "",
    override val criteria: List<Criteria> = emptyList(),
    override val modifiers: List<Modifier> = emptyList(),
    override val triggers: List<Ref<TriggerableEntry>> = emptyList()
) : ActionEntry, KoinComponent {

    private val sessionService: MenuSessionService by inject()

    override fun ActionTrigger.execute() {
        if (closeSound != Sound.EMPTY) {
            closeSound.play(player, context)
        }
        sessionService.stopSession(player)
    }
}
