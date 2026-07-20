package com.btc.shops.ui

import btcrenaud.gui.api.GenericButtonResolverLayout
import btcrenaud.gui.api.GuiSlot
import btcrenaud.gui.api.GuiSlotInteraction
import btcrenaud.gui.api.InteractionType
import btcrenaud.gui.api.MenuLayout
import btcrenaud.gui.api.Viewport
import btcrenaud.gui.services.MenuSessionService
import com.btc.shops.manifest.ShopDefinitionEntry
import org.bukkit.entity.Player

/**
 * Resolves [ShopButtonType] tagged placeholders into dynamic GUI slots.
 *
 * Delegates to [GenericButtonResolverLayout] with prefix "shop_button:".
 * Supports TYPE:param syntax (e.g., "BUY_1:10") for sub-menu amount disambiguation.
 *
 * Click types are resolved from [ShopDefinitionEntry.interactionConfig].
 */
class ShopButtonResolverLayout(
    inner: MenuLayout,
    private val player: Player,
    private val definition: ShopDefinitionEntry,
    private val page: Int = 0,
    /** Current shop item index (set for sub-menus, -1 for main shop) */
    private val itemIndex: Int = -1,
    override val id: String? = null,
) : MenuLayout {

    companion object {
        const val COMMAND = "typewritershop"
    }

    private val delegate = GenericButtonResolverLayout(
        inner = inner,
        prefix = "shop_button:",
        resolver = { type, p, slot -> resolveButton(type, p, slot) },
        id = id,
    )

    override val innerLayout: MenuLayout? get() = delegate.innerLayout

    override fun getSlots(
        session: MenuSessionService.ActiveSession,
        viewport: Viewport,
    ): List<GuiSlot> = delegate.getSlots(session, viewport)

    override val virtualWidth: Int get() = delegate.virtualWidth
    override val virtualHeight: Int get() = delegate.virtualHeight

    private fun resolveButton(rawType: String, p: Player, slot: GuiSlot): GuiSlot? {
        val colonIdx = rawType.indexOf(':')
        val typeName = if (colonIdx >= 0) rawType.substring(0, colonIdx) else rawType
        val param = if (colonIdx >= 0) rawType.substring(colonIdx + 1) else null

        val buttonType = try {
            ShopButtonType.valueOf(typeName)
        } catch (_: IllegalArgumentException) {
            return null
        }

        val cmd = COMMAND
        val defId = definition.id
        val playerName = p.name
        val ic = definition.interactionConfig

        return when (buttonType) {
            ShopButtonType.SHOP_ITEM -> null

            // ── Navigation (uses navigationClick) ──
            ShopButtonType.NEXT_PAGE -> slot.copy(interactions = mapOf(
                ic.navigationClick to GuiSlotInteraction(commands = listOf("$cmd next_page $defId 0 $playerName"))
            ))
            ShopButtonType.PREV_PAGE -> slot.copy(interactions = mapOf(
                ic.navigationClick to GuiSlotInteraction(commands = listOf("$cmd prev_page $defId 0 $playerName"))
            ))
            ShopButtonType.INFO -> slot.copy(interactions = mapOf(
                ic.navigationClick to GuiSlotInteraction(commands = listOf("$cmd info $defId $itemIndex $playerName"))
            ))
            ShopButtonType.CLOSE -> slot.copy(interactions = mapOf(
                ic.navigationClick to GuiSlotInteraction(commands = listOf("gui:close"))
            ))
            ShopButtonType.BACK -> slot.copy(interactions = mapOf(
                ic.navigationClick to GuiSlotInteraction(commands = listOf("gui:back"))
            ))

            // ── Buy buttons ──
            ShopButtonType.BUY_1 -> {
                val amount = param?.toIntOrNull() ?: 1
                slot.copy(interactions = mapOf(
                    ic.buy1Click to GuiSlotInteraction(commands = listOf("$cmd buy $defId $itemIndex $playerName $amount"))
                ))
            }
            ShopButtonType.BUY_STACK -> slot.copy(interactions = mapOf(
                ic.buyStackClick to GuiSlotInteraction(commands = listOf("$cmd buystack $defId $itemIndex $playerName"))
            ))
            ShopButtonType.BUY_MAX -> slot.copy(interactions = mapOf(
                ic.buyMaxClick to GuiSlotInteraction(commands = listOf("$cmd buymax $defId $itemIndex $playerName"))
            ))
            ShopButtonType.BUY_SUBMENU -> slot.copy(interactions = mapOf(
                ic.buySubmenuClick to GuiSlotInteraction(commands = listOf("$cmd submenu_buy $defId $itemIndex $playerName"))
            ))
            ShopButtonType.BUY_CUSTOM -> slot.copy(interactions = mapOf(
                ic.buyCustomClick to GuiSlotInteraction(commands = listOf("$cmd amount_buy $defId $itemIndex $playerName"))
            ))

            // ── Sell buttons ──
            ShopButtonType.SELL_1 -> {
                val amount = param?.toIntOrNull() ?: 1
                slot.copy(interactions = mapOf(
                    ic.sell1Click to GuiSlotInteraction(commands = listOf("$cmd sell $defId $itemIndex $playerName $amount"))
                ))
            }
            ShopButtonType.SELL_STACK -> slot.copy(interactions = mapOf(
                ic.sellStackClick to GuiSlotInteraction(commands = listOf("$cmd sellstack $defId $itemIndex $playerName"))
            ))
            ShopButtonType.SELL_ALL -> slot.copy(interactions = mapOf(
                ic.sellAllClick to GuiSlotInteraction(commands = listOf("$cmd sellall $defId $itemIndex $playerName"))
            ))
            ShopButtonType.SELL_SUBMENU -> slot.copy(interactions = mapOf(
                ic.sellSubmenuClick to GuiSlotInteraction(commands = listOf("$cmd submenu_sell $defId $itemIndex $playerName"))
            ))
            ShopButtonType.SELL_CUSTOM -> slot.copy(interactions = mapOf(
                ic.sellCustomClick to GuiSlotInteraction(commands = listOf("$cmd amount_sell $defId $itemIndex $playerName"))
            ))
        }
    }
}
