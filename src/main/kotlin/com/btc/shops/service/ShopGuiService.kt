package com.btc.shops.service

import btcrenaud.gui.GuiSlotBuilder
import btcrenaud.gui.GuiType
import btcrenaud.gui.InventorySize
import btcrenaud.gui.LayoutData
import btcrenaud.gui.SimpleLayoutData
import btcrenaud.gui.api.*
import btcrenaud.gui.services.MenuSessionService
import com.btc.shops.manifest.*
import com.btc.shops.ui.AugmentedSimpleLayout
import com.btc.shops.ui.ShopButtonResolverLayout
import com.btc.shops.ui.ShopButtonType
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.engine.paper.entry.matches
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.minimessage.MiniMessage
import com.typewritermc.engine.paper.utils.item.Item
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

@Singleton
class ShopGuiService(
    private val plugin: JavaPlugin,
    private val priceService: PriceService,
    private val stockService: StockService,
    private val resetService: ResetService,
    private val shopPoolService: ShopPoolService
) {

    private val miniMessage = MiniMessage.miniMessage()
    private val openSessions = ConcurrentHashMap<UUID, ShopSession>()

    private companion object {
        /** Marker tag identifying a slot that a shop item is placed into. */
        const val SHOP_ITEM_BUTTON_TYPE = "shop_button:SHOP_ITEM"
    }

    private data class ShopSession(
        val definition: ShopDefinitionEntry,
        var page: Int,
        /** Currently selected item index (for sub-menus). -1 if none. */
        var selectedItemIndex: Int = -1,
    )

    private fun String.toComponent() = miniMessage.deserialize(this)

    private fun applyPlaceholders(player: Player, text: String): String {
        return if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            PlaceholderAPI.setPlaceholders(player, text)
        } else text
    }

    private fun formatPrice(value: Double, definition: ShopDefinitionEntry): String {
        val formatted = String.format("%.2f", value)
        return definition.priceFormat
            .replace("{amount}", formatted)
            .replace("{currency}", definition.currencySymbol)
    }

    // ──────────────────────────────────────────────
    //  Open / Refresh / Navigate
    // ──────────────────────────────────────────────

    fun open(player: Player, definition: ShopDefinitionEntry, page: Int = 0, delayTicks: Long = 3L) {
        val task = Runnable {
            if (!definition.criteria.matches(player)) {
                player.sendMessage(definition.criteriaFailMessage.toComponent())
                return@Runnable
            }

            if (resetService.shouldReset(definition)) {
                definition.items.forEachIndexed { index, cfg ->
                    stockService.reset(definition.id, index, cfg.dynamicPricing.stockMax)
                }
            }

            openFromLayoutPool(player, definition, page)
        }

        if (delayTicks <= 0L) {
            player.scheduler.run(plugin, Consumer { task.run() }, null)
        } else {
            player.scheduler.runDelayed(plugin, Consumer { task.run() }, null, delayTicks)
        }
    }

    fun refresh(player: Player) {
        val session = openSessions[player.uniqueId] ?: return
        open(player, session.definition, session.page, delayTicks = 0L)
    }

    fun navigateNext(player: Player) {
        val session = openSessions[player.uniqueId] ?: return
        val effectiveItems = shopPoolService.resolveItems(session.definition)
        val pageSize = countItemSlots(session.definition)
        val maxPage = if (pageSize > 0) ((effectiveItems.size - 1) / pageSize).coerceAtLeast(0) else 0
        val nextPage = (session.page + 1).coerceAtMost(maxPage)
        if (nextPage != session.page) {
            open(player, session.definition, nextPage, delayTicks = 0L)
        }
    }

    fun navigatePrev(player: Player) {
        val session = openSessions[player.uniqueId] ?: return
        val prevPage = (session.page - 1).coerceAtLeast(0)
        if (prevPage != session.page) {
            open(player, session.definition, prevPage, delayTicks = 0L)
        }
    }

    fun close(player: Player) {
        openSessions.remove(player.uniqueId)
        player.closeInventory()
    }

    // ──────────────────────────────────────────────
    //  Main Shop — Layout Pool Mode
    // ──────────────────────────────────────────────

    private fun openFromLayoutPool(
        player: Player,
        definition: ShopDefinitionEntry,
        page: Int,
    ) {
        val ctx = context { }
        val effectiveItems = shopPoolService.resolveItems(definition)
        val rows = definition.rows.coerceIn(1, 6)
        val size = when (rows) {
            1 -> InventorySize.SIZE_9
            2 -> InventorySize.SIZE_18
            3 -> InventorySize.SIZE_27
            4 -> InventorySize.SIZE_36
            5 -> InventorySize.SIZE_45
            else -> InventorySize.SIZE_54
        }

        // 1. Extract SHOP_ITEM positions directly from raw layout data (no parse needed).
        //    Repetition is expanded by GuiSlotBuilder so SHOP_ITEM markers spread exactly like
        //    every other repeated item — a local copy of this maths used `gap + 1` as the step,
        //    ignored `repeatY`, and gated on `count > 1`, which collapsed markers to a single
        //    slot whenever the editor left `count` unset (serialized as 0).
        data class SlotPos(val x: Int, val y: Int)
        val itemPositions = mutableListOf<SlotPos>()

        val cleanedPool = definition.layoutPool.mapNotNull { layoutData ->
            when (layoutData) {
                is SimpleLayoutData -> {
                    val filteredItems = layoutData.items.filter { item ->
                        if (item.buttonType == SHOP_ITEM_BUTTON_TYPE) {
                            GuiSlotBuilder.expandPositions(item).forEach { (px, py) ->
                                itemPositions.add(SlotPos(px, py))
                            }
                            false // Remove from cleaned layout
                        } else true
                    }
                    if (filteredItems.isEmpty()) null else layoutData.copy(items = filteredItems)
                }
                else -> layoutData
            }
        }

        // 2. Parse the cleaned pool once
        val cleanedPoolMap = cleanedPool.filterNotNull().associateBy { it.id }
        val baseLayout: MenuLayout = if (cleanedPoolMap.containsKey(definition.mainLayoutId)) {
            LayoutParser.parse(player, ctx, GuiType.CUSTOM, size.slots, cleanedPoolMap, cleanedPoolMap[definition.mainLayoutId]!!)
        } else {
            EmptyLayout
        }

        // 3. Build dynamic shop item slots with configurable click actions
        val pageSize = itemPositions.size
        val startIndex = page * pageSize
        val pageItems = effectiveItems.drop(startIndex).take(pageSize)
        val dynamicSlots = mutableListOf<GuiSlot>()

        pageItems.forEachIndexed { i, cfg ->
            if (i >= itemPositions.size) return@forEachIndexed
            val pos = itemPositions[i]

            if (!cfg.criteria.matches(player)) {
                val lockedStack = definition.lockedItem.get(player).build(player)
                dynamicSlots.add(GuiSlot(
                    x = pos.x, y = pos.y,
                    item = lockedStack,
                    allowPickup = false,
                ))
                return@forEachIndexed
            }

            val realItemIndex = effectiveItems.indexOf(cfg)
            val stack = buildShopItem(cfg, player, definition)
            val interactions = buildItemClickInteractions(cfg, definition, realItemIndex, player)
            dynamicSlots.add(GuiSlot(
                x = pos.x, y = pos.y,
                item = stack,
                allowPickup = false,
                interactions = interactions,
            ))
        }

        // 4. Inject dynamic slots
        val augmentedLayout = AugmentedSimpleLayout(
            inner = baseLayout,
            dynamicSlots = dynamicSlots,
        )

        // 5. Wrap with button resolver
        val resolvedLayout = ShopButtonResolverLayout(
            inner = augmentedLayout,
            player = player,
            definition = definition,
            page = page,
            itemIndex = -1, // Main shop: no selected item
        )

        // 6. Build title and register
        val rawTitle = applyPlaceholders(player, definition.title)
        val menuDef = MenuDefinition(
            id = "shop:${definition.id}:${player.uniqueId}",
            type = GuiType.CUSTOM,
            title = rawTitle.toComponent(),
            rawTitle = rawTitle,
            size = size,
            layout = resolvedLayout,
        )

        openSessions[player.uniqueId] = ShopSession(definition, page)
        MenuSessionService.register(player, menuDef, pushHistory = page == 0)
    }

    // ──────────────────────────────────────────────
    //  Sub-Menu — Layout Pool Mode
    // ──────────────────────────────────────────────

    fun openSubMenu(
        player: Player,
        definition: ShopDefinitionEntry,
        cfg: ShopItemConfig,
        itemIndex: Int,
        buy: Boolean,
    ) {
        val task = Runnable {
            val ctx = context { }
            val rows = definition.rows.coerceIn(1, 6)
            val size = when (rows) {
                1 -> InventorySize.SIZE_9
                2 -> InventorySize.SIZE_18
                3 -> InventorySize.SIZE_27
                4 -> InventorySize.SIZE_36
                5 -> InventorySize.SIZE_45
                else -> InventorySize.SIZE_54
            }

            // The sub-menu is just another layout in the shop's single pool, picked by id.
            val pool = definition.layoutPool.associateBy { it.id }
            val baseLayout: MenuLayout = if (pool.containsKey(definition.subMenuLayoutId)) {
                LayoutParser.parse(player, ctx, GuiType.CUSTOM, size.slots, pool, pool[definition.subMenuLayoutId]!!)
            } else {
                EmptyLayout
            }

            // Wrap with button resolver (passes itemIndex for sub-menu commands)
            val resolvedLayout = ShopButtonResolverLayout(
                inner = baseLayout,
                player = player,
                definition = definition,
                page = 0,
                itemIndex = itemIndex,
            )

            // Build title
            val titleTemplate = definition.subMenuTitle.replace("{item_name}", cfg.name.ifEmpty { "Item" })
            val rawTitle = applyPlaceholders(player, titleTemplate)
            val menuDef = MenuDefinition(
                id = "shop:submenu:${definition.id}:${itemIndex}:${player.uniqueId}",
                type = GuiType.CUSTOM,
                title = rawTitle.toComponent(),
                rawTitle = rawTitle,
                size = size,
                layout = resolvedLayout,
            )

            // Update session with selected item
            val session = openSessions[player.uniqueId] ?: return@Runnable
            session.selectedItemIndex = itemIndex

            MenuSessionService.register(player, menuDef, pushHistory = true)
        }

        player.scheduler.run(plugin, Consumer { task.run() }, null)
    }

    // ──────────────────────────────────────────────
    //  Item Building
    // ──────────────────────────────────────────────

    private fun buildItemClickInteractions(
        cfg: ShopItemConfig,
        definition: ShopDefinitionEntry,
        itemIndex: Int,
        player: Player
    ): Map<InteractionType, GuiSlotInteraction> {
        val interactions = mutableMapOf<InteractionType, GuiSlotInteraction>()
        val cmd = ShopButtonResolverLayout.COMMAND
        val defId = definition.id
        val playerName = player.name
        val config = definition.interactionConfig

        fun actionToCommands(action: ShopClickAction): List<String>? = when (action) {
            ShopClickAction.NONE -> null
            ShopClickAction.BUY_1 -> listOf("${cmd}buy $defId $itemIndex 1")
            ShopClickAction.SELL_1 -> listOf("${cmd}sell $defId $itemIndex 1")
            ShopClickAction.BUY_STACK -> {
                val stackAmount = cfg.item.get(player).build(player).maxStackSize
                listOf("${cmd}buystack $defId $itemIndex")
            }
            ShopClickAction.SELL_STACK -> listOf("${cmd}sellstack $defId $itemIndex")
            ShopClickAction.BUY_MAX -> listOf("${cmd}buymax $defId $itemIndex")
            ShopClickAction.SELL_ALL -> listOf("${cmd}sellall $defId $itemIndex")
            ShopClickAction.BUY_SUBMENU -> listOf("${cmd}submenu_buy $defId $itemIndex")
            ShopClickAction.SELL_SUBMENU -> listOf("${cmd}submenu_sell $defId $itemIndex")
            ShopClickAction.BUY_CUSTOM -> listOf("${cmd}amount_buy $defId $itemIndex")
            ShopClickAction.SELL_CUSTOM -> listOf("${cmd}amount_sell $defId $itemIndex")
        }

        fun clickForAction(action: ShopClickAction, config: ShopInteractionConfig): InteractionType? = when (action) {
            ShopClickAction.NONE -> null
            ShopClickAction.BUY_1 -> config.buy1Click
            ShopClickAction.SELL_1 -> config.sell1Click
            ShopClickAction.BUY_STACK -> config.buyStackClick
            ShopClickAction.SELL_STACK -> config.sellStackClick
            ShopClickAction.BUY_MAX -> config.buyMaxClick
            ShopClickAction.SELL_ALL -> config.sellAllClick
            ShopClickAction.BUY_SUBMENU -> config.buySubmenuClick
            ShopClickAction.SELL_SUBMENU -> config.sellSubmenuClick
            ShopClickAction.BUY_CUSTOM -> config.buyCustomClick
            ShopClickAction.SELL_CUSTOM -> config.sellCustomClick
        }

        // Primary action
        actionToCommands(cfg.primaryAction)?.let { cmds ->
            val click = clickForAction(cfg.primaryAction, config)
            if (click != null) interactions[click] = GuiSlotInteraction(commands = cmds)
        }

        // Secondary action
        actionToCommands(cfg.secondaryAction)?.let { cmds ->
            val click = clickForAction(cfg.secondaryAction, config)
            if (click != null) interactions[click] = GuiSlotInteraction(commands = cmds)
        }

        // Shift action
        actionToCommands(cfg.shiftAction)?.let { cmds ->
            val click = clickForAction(cfg.shiftAction, config)
            if (click != null) interactions[click] = GuiSlotInteraction(commands = cmds)
        }

        return interactions
    }

    private fun buildShopItem(cfg: ShopItemConfig, player: Player, definition: ShopDefinitionEntry): ItemStack {
        val stack = cfg.item.get(player).build(player)
        val meta = stack.itemMeta ?: return stack

        if (cfg.name.isNotEmpty()) {
            meta.displayName(applyPlaceholders(player, cfg.name).toComponent())
        }

        val baseLore = meta.lore()?.toList() ?: emptyList()
        val customLore = cfg.lore.flatMap { line ->
            applyPlaceholders(player, line).split("\n").map { it.toComponent() }
        }
        val priceLore = definition.priceLore.flatMap { line ->
            var processed = applyPlaceholders(player, line)
            var show = true
            if (processed.contains("{buy}")) {
                if (cfg.buyEnabled) {
                    val price = getBuyPrice(definition, cfg, 1)
                    if (price > 0) processed = processed.replace("{buy}", formatPrice(price, definition)) else show = false
                } else {
                    show = false
                }
            }
            if (processed.contains("{sell}")) {
                if (cfg.sellEnabled) {
                    val price = getSellPrice(definition, cfg, 1)
                    if (price > 0) processed = processed.replace("{sell}", formatPrice(price, definition)) else show = false
                } else {
                    show = false
                }
            }
            if (show) processed.split("\n").map { it.toComponent() } else emptyList()
        }

        val lore = baseLore + customLore + priceLore
        if (lore.isNotEmpty()) meta.lore(lore)
        stack.itemMeta = meta
        return stack
    }

    // ──────────────────────────────────────────────
    //  Price Helpers
    // ──────────────────────────────────────────────

    /**
     * Number of item slots one page holds. MUST use the same expansion as
     * [openFromLayoutPool], otherwise paging drifts from what is actually rendered
     * (summing raw `count` reported 0 slots whenever the editor left `count` unset).
     */
    private fun countItemSlots(definition: ShopDefinitionEntry): Int {
        var count = 0
        definition.layoutPool.forEach { layoutData ->
            when (layoutData) {
                is SimpleLayoutData -> {
                    layoutData.items.forEach { item ->
                        if (item.buttonType == SHOP_ITEM_BUTTON_TYPE) {
                            count += GuiSlotBuilder.expandPositions(item).size
                        }
                    }
                }
                else -> {}
            }
        }
        return count
    }

    private fun getBuyPrice(definition: ShopDefinitionEntry, cfg: ShopItemConfig, amount: Int): Double {
        return when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedBuyPrice * amount
            PriceMode.DYNAMIC -> {
                val index = definition.items.indexOf(cfg)
                val stock = stockService.getStock(definition.id, index, cfg.dynamicPricing.stockMax)
                priceService.calculateBuyPrice(stock, cfg.dynamicPricing) * amount
            }
        }
    }

    private fun getSellPrice(definition: ShopDefinitionEntry, cfg: ShopItemConfig, amount: Int): Double {
        return when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedSellPrice * amount
            PriceMode.DYNAMIC -> {
                val index = definition.items.indexOf(cfg)
                val stock = stockService.getStock(definition.id, index, cfg.dynamicPricing.stockMax)
                priceService.calculateSellPrice(stock, cfg.dynamicPricing) * amount
            }
        }
    }
}
