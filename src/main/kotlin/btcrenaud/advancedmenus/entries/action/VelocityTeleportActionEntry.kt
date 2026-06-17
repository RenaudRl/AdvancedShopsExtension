package btcrenaud.advancedmenus.entries.action

import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.ActionEntry
import com.typewritermc.engine.paper.entry.entries.ActionTrigger
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.entry.entries.ConstVar
import btcrenaud.advancedmenus.services.BungeeService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Entry("velocity_teleport", "Velocity Teleport", Colors.RED, "mdi:server-network")
class VelocityTeleportActionEntry(
    @Help("The target server name")
    val server: Var<String> = ConstVar(""),

    override val id: String = "",
    override val name: String = "",
    override val criteria: List<Criteria> = emptyList(),
    override val modifiers: List<Modifier> = emptyList(),
    override val triggers: List<Ref<TriggerableEntry>> = emptyList()
) : ActionEntry, KoinComponent {

    private val bungeeService: BungeeService by inject()

    override fun ActionTrigger.execute() {
        val srv = server.get(player, context)
        if (srv.isNotEmpty()) {
            bungeeService.connect(player, srv)
        }
    }
}
