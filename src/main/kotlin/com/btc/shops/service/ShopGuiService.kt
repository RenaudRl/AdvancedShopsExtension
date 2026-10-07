package com.btc.shops.service

import btcrenaud.gui.GuiSlotBuilder
import btcrenaud.gui.GuiType
import btcrenaud.gui.InventorySize
import btcrenaud.gui.LayoutData
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
import com.typewritermc.engine.paper.logger
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
    private val shopPoolService: ShopPoolService,
    private val itemPriceService: ItemPriceService,
) {

    private val miniMessage = MiniMessage.miniMessage()
    private val openSessions = ConcurrentHashMap<UUID, ShopSession>()

    private companion object {
        /** Columns of an inventory: the width of a standalone shop's content area. */
        const val INVENTORY_COLUMNS = 9
        private const val MAX_REPORTED_PLACEMENTS = 256

        /** Marker button type identifying a slot that a shop item is placed into. */
        const val SHOP_ITEM_BUTTON_TYPE = "SHOP_ITEM"

        /**
         * True when this layout item is a SHOP_ITEM marker. In GuiItemData the type and the
         * prefix are SEPARATE fields (buttonType="SHOP_ITEM", buttonPrefix="shop_button:") —
         * comparing buttonType against the concatenated "shop_button:SHOP_ITEM" never matched,
         * so markers were left in the layout as inert STRUCTURE_VOIDs and no shop item rendered.
         * The prefix is optional: a shop layout's SHOP_ITEM marker is unambiguous.
         */
        fun isShopItemMarker(item: btcrenaud.gui.GuiItemData): Boolean {
            val type = item.buttonType?.takeIf { it.isNotEmpty() } ?: return false
            val prefix = item.buttonPrefix
            return type == SHOP_ITEM_BUTTON_TYPE &&
                (prefix == null || prefix.isEmpty() || prefix == "shop_button:")
        }
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

    fun open(
        player: Player,
        definition: ShopDefinitionEntry,
        page: Int = 0,
        delayTicks: Long = 3L,
        targetViewId: String? = null,
        pushHistory: Boolean = true,
    ) {
        val task = Runnable {
            if (!definition.criteria.matches(player)) {
                player.sendMessage(definition.criteriaFailMessage.toComponent())
                return@Runnable
            }

            resetService.resetIfDue(definition)

            openFromLayoutPool(player, definition, page, targetViewId, pushHistory)
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
        val plan = planPage(session.definition, shopPoolService.resolveItems(session.definition))
        val nextPage = (session.page + 1).coerceAtMost(plan.lastPage)
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
        targetViewId: String? = null,
        pushHistory: Boolean = true,
    ) {
        val ctx = context { }
        val effectiveItems = shopPoolService.resolveItems(definition)
        val tabMode = definition.usesTabMode
        val standaloneRows = definition.rows.coerceIn(1, 6)
        val standaloneSize = when (standaloneRows) {
            1 -> InventorySize.SIZE_9
            2 -> InventorySize.SIZE_18
            3 -> InventorySize.SIZE_27
            4 -> InventorySize.SIZE_36
            5 -> InventorySize.SIZE_45
            else -> InventorySize.SIZE_54
        }
        // A tab-mode shop is rendered inside the shared chassis' fixed 6-row inventory — the
        // chassis owns the size, `rows` only matters when the shop still opens on its own.
        val size = if (tabMode) InventorySize.SIZE_54 else standaloneSize

        // 1. Where the items go: pinned items at their own slot, the others on the SHOP_ITEM
        //    markers (see [planPage]). The markers themselves are removed from the layout below.
        val plan = planPage(definition, effectiveItems)
        reportPlacement(definition, plan)

        // A layout emptied of its markers is KEPT, empty. Dropping it made it unreachable in the
        // pool, and the chassis frame pointing at it then rendered nothing — a shop screen whose
        // whole grid is made of markers, or a still-empty tab, silently disappeared.
        val cleanedPool = definition.layoutPool.map { layoutData ->
            layoutData.copy(items = layoutData.items.filterNot(::isShopItemMarker))
        }

        // 2. Resolve the base layout — either the shop's own single layout (standalone), or the
        //    shared chassis with the shop's views merged in (tab mode), exactly like every other
        //    extension resolves against a shell via MenuViewSupport.
        var activeViewId: String? = null
        var breadcrumb: List<String> = emptyList()
        val baseLayout: MenuLayout
        val rawTitle: String
        // The extended chassis also occupies the player's inventory and hotbar. Known only after
        // the chassis is resolved, but read when the MenuDefinition is built.
        var extendedChassis = false

        if (tabMode) {
            val inherited = MenuViewSupport.inherit(
                baseMenuId = definition.baseMenuId,
                ownPool = cleanedPool,
                ownViews = definition.views,
                ownMainLayoutId = definition.mainLayoutId.ifBlank { null },
                ownDefaultViewId = definition.defaultViewId,
            )
            val rootLayoutId = MenuViewSupport.extendedRootLayoutId(inherited, inherited.mainLayoutId)
            extendedChassis = MenuViewSupport.isExtendedRoot(rootLayoutId)
            val resolved = MenuViewSupport.resolve(
                player = player,
                context = ctx,
                guiType = GuiType.CUSTOM,
                totalSize = size.slots,
                pool = inherited.pool,
                mainLayoutId = rootLayoutId,
                views = inherited.views,
                defaultViewId = inherited.defaultViewId,
                targetViewId = targetViewId,
                baseTitle = definition.title,
            )
            baseLayout = resolved.layout
            rawTitle = resolved.rawTitle
            activeViewId = resolved.activeViewId
            breadcrumb = resolved.breadcrumb
        } else {
            val cleanedPoolMap: Map<String, LayoutData> = cleanedPool.associateBy { it.id }
            baseLayout = if (cleanedPoolMap.containsKey(definition.mainLayoutId)) {
                LayoutParser.parse(player, ctx, GuiType.CUSTOM, size.slots, cleanedPoolMap, cleanedPoolMap[definition.mainLayoutId]!!)
            } else {
                EmptyLayout
            }
            rawTitle = applyPlaceholders(player, definition.title)
        }

        // 3. Build dynamic shop item slots with configurable click actions. SHOP_ITEM markers are
        //    declared in the shop's own layout, in LOCAL coordinates — in tab mode that layout
        //    ends up nested inside the chassis' content frame, which auto-offsets its static items
        //    but not slots injected after the fact via AugmentedSimpleLayout, so the marker
        //    positions (and the filler below) must be shifted by contentX/contentY by hand.
        val offsetX = if (tabMode) definition.contentX else 0
        val offsetY = if (tabMode) definition.contentY else 0
        val dynamicSlots = mutableListOf<GuiSlot>()

        // Cells that actually received a product this page. Markers beyond the product count
        // stay free so the filler covers them — otherwise a shop with fewer products than marker
        // slots renders holes where the leftover markers were.
        val usedPositions = mutableSetOf<Pair<Int, Int>>()

        // Articles whose stock is shown live: the menu is redrawn only when one of them changes.
        val watched = mutableListOf<StockWatch.Item>()
        val watchedConfigs = mutableMapOf<Int, ShopItemConfig>()

        plan.itemsOnPage(page).forEach { (indexed, local) ->
            val cfg = indexed.value
            val realItemIndex = indexed.index
            val pos = local.x + offsetX to local.y + offsetY
            usedPositions.add(pos)

            if (!cfg.criteria.matches(player)) {
                val lockedStack = definition.lockedItem.get(player).build(player)
                dynamicSlots.add(GuiSlot(
                    x = pos.first, y = pos.second,
                    item = lockedStack,
                    allowPickup = false,
                ))
                return@forEach
            }

            val stack = buildShopItem(cfg, realItemIndex, player, definition)
            val interactions = buildItemClickInteractions(cfg, definition, realItemIndex, player)
            val refreshTicks = liveRefreshTicks(cfg, definition)
            if (refreshTicks > 0) {
                watched.add(StockWatch.Item(realItemIndex, refreshTicks, stockOf(player, definition, realItemIndex, cfg)))
                watchedConfigs[realItemIndex] = cfg
            }
            dynamicSlots.add(GuiSlot(
                x = pos.first, y = pos.second,
                item = stack,
                allowPickup = false,
                interactions = interactions,
                // Rebuilt on every redraw, which only happens when a watched stock has changed.
                itemProvider = if (refreshTicks > 0) { viewer -> buildShopItem(cfg, realItemIndex, viewer, definition) } else null,
            ))
        }

        // 4. Fill the slots the author left empty, then inject everything. In tab mode the filler
        //    must stay confined to the shop's own content rectangle — the chassis' domain strip
        //    and action row live in the same 6-row inventory and must not be painted over.
        val occupied = mutableSetOf<Pair<Int, Int>>()
        if (tabMode) {
            cleanedPool.forEach { layoutData ->
                layoutData.items.forEach { item ->
                    GuiSlotBuilder.expandPositions(item).forEach { (px, py) ->
                        occupied.add(px + offsetX to py + offsetY)
                    }
                }
            }
        } else {
            cleanedPool.firstOrNull { it.id == definition.mainLayoutId }?.items?.forEach { item ->
                occupied.addAll(GuiSlotBuilder.expandPositions(item))
            }
        }
        occupied.addAll(usedPositions)
        dynamicSlots.addAll(
            if (tabMode) {
                buildFillerSlots(player, definition, definition.contentColumns, definition.contentRows, offsetX, offsetY, occupied)
            } else {
                buildFillerSlots(player, definition, INVENTORY_COLUMNS, standaloneRows, 0, 0, occupied)
            }
        )

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
        val menuDef = MenuDefinition(
            id = "shop:${definition.id}:${player.uniqueId}",
            type = GuiType.CUSTOM,
            title = rawTitle.toComponent(),
            rawTitle = rawTitle,
            size = size,
            layout = resolvedLayout,
            activeViewId = activeViewId,
            breadcrumb = breadcrumb,
            viewSwitcher = if (tabMode) { p, viewId ->
                open(p, definition, page = 0, delayTicks = 0L, targetViewId = viewId, pushHistory = false)
            } else null,
        ).also {
            it.extendToPlayerInventory = extendedChassis
        }

        val shopSession = ShopSession(definition, page)
        openSessions[player.uniqueId] = shopSession
        MenuSessionService.register(
            player,
            menuDef,
            pushHistory = if (tabMode) pushHistory else page == 0,
        )
        watchStocks(player, definition, shopSession, menuDef.id, StockWatch(watched, System.currentTimeMillis()), watchedConfigs)
    }

    /**
     * Ticks between two looks at [cfg]'s stock, 0 when it is never looked at.
     *
     * Only an article with a finite stock can change while the shop is open; an unlimited one
     * is not watched at all.
     */
    private fun liveRefreshTicks(cfg: ShopItemConfig, definition: ShopDefinitionEntry): Int =
        if (cfg.dynamicPricing.stockMax <= 0) 0
        else RefreshRate.effectiveTicks(cfg.refreshTicks, definition.defaultRefreshTicks)

    /**
     * Looks at the watched stocks on the player's own scheduler and redraws the open menu only
     * when one of them has changed: a shop whose stocks do not move costs a few cached reads per
     * period and no redraw. The task ends with the shop session, the menu or the player.
     */
    private fun watchStocks(
        player: Player,
        definition: ShopDefinitionEntry,
        session: ShopSession,
        menuId: String,
        watch: StockWatch,
        configs: Map<Int, ShopItemConfig>,
    ) {
        val period = watch.fastestPeriodTicks ?: return
        player.scheduler.runAtFixedRate(plugin, { task ->
            val stillOpen = openSessions[player.uniqueId] === session &&
                MenuSessionService.getSession(player)?.definition?.id == menuId
            if (!stillOpen) {
                task.cancel()
                return@runAtFixedRate
            }
            val changed = watch.poll(System.currentTimeMillis()) { index ->
                configs[index]?.let { stockOf(player, definition, index, it) } ?: 0
            }
            if (changed) MenuSessionService.refresh(player)
        }, null, period, period)
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
            val pool: Map<String, LayoutData> = definition.layoutPool.associateBy { it.id }
            val baseLayout: MenuLayout = if (pool.containsKey(definition.subMenuLayoutId)) {
                LayoutParser.parse(player, ctx, GuiType.CUSTOM, size.slots, pool, pool[definition.subMenuLayoutId]!!)
            } else {
                EmptyLayout
            }

            // Same filler treatment as the main menu, so the sub-menu isn't full of holes.
            val subOccupied = mutableSetOf<Pair<Int, Int>>()
            definition.layoutPool.firstOrNull { it.id == definition.subMenuLayoutId }?.items?.forEach { item ->
                subOccupied.addAll(GuiSlotBuilder.expandPositions(item))
            }
            val filledLayout = AugmentedSimpleLayout(
                inner = baseLayout,
                dynamicSlots = buildFillerSlots(player, definition, 9, rows, 0, 0, subOccupied),
            )

            // Wrap with button resolver (passes itemIndex for sub-menu commands)
            val resolvedLayout = ShopButtonResolverLayout(
                inner = filledLayout,
                player = player,
                definition = definition,
                page = 0,
                itemIndex = itemIndex,
            )

            // Build title
            val titleTemplate = definition.subMenuTitle.replace("{item_name}", cfg.name.ifEmpty { definition.defaultItemName })
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

    /**
     * Decorative background for every slot the author left empty.
     *
     * [ShopDefinitionEntry.fillerItem] was configured-but-never-read: shops rendered with holes
     * instead of the background the author picked. Only genuinely free positions are filled, so
     * the filler can never cover a layout button or a shop item slot. The rectangle is bounded
     * (rather than the whole inventory) so a tab-mode shop's filler never paints over the shared
     * chassis' domain strip or action row, which live in the same 6-row inventory.
     */
    private fun buildFillerSlots(
        player: Player,
        definition: ShopDefinitionEntry,
        width: Int,
        height: Int,
        offsetX: Int,
        offsetY: Int,
        occupied: Set<Pair<Int, Int>>,
    ): List<GuiSlot> {
        val resolved = definition.fillerItem.get(player)
        if (resolved == Item.Empty) return emptyList()
        val stack = resolved.build(player)
        val slots = mutableListOf<GuiSlot>()
        for (row in 0 until height) {
            for (column in 0 until width) {
                val pos = (column + offsetX) to (row + offsetY)
                if (pos in occupied) continue
                slots.add(GuiSlot(x = pos.first, y = pos.second, item = stack.clone(), allowPickup = false))
            }
        }
        return slots
    }

    /**
     * Substitutes a shop's format tokens on a raw line.
     *
     * `{buy}` / `{sell}` / `{stock}` / `{stock_max}` / `{currency}` / `{item}` / `{item_price}`
     * are configuration tokens, not placeholders: they are replaced here, on the string, after
     * the placeholder pass and before MiniMessage parsing.
     *
     * @param baseName the item's own display name, for `{item}`.
     * @return the substituted line, or `null` when it asks for an unavailable price — a
     *   "Sell: {sell}" line on an unsellable item must disappear, not render empty.
     */
    private fun expandItemTokens(
        template: String,
        player: Player,
        cfg: ShopItemConfig,
        index: Int,
        definition: ShopDefinitionEntry,
        baseName: String,
    ): String? {
        var out = template
        if (out.contains("{item_price}")) {
            // A line asking for the price in items disappears on an article that has none, instead
            // of rendering empty — same rule as `{buy}` on an unbuyable article. A misconfigured
            // price disappears too: the fault is logged at purchase time, where it is refused.
            val resolution = itemPriceService.applicablePrice(definition, cfg) ?: return null
            val lines = (resolution as? ItemPriceRule.Resolution.Price<ItemCost>)?.lines ?: return null
            out = out.replace("{item_price}", itemPriceService.describe(player, lines, definition))
        }
        if (out.contains("{buy}")) {
            if (!cfg.buyEnabled) return null
            val price = getBuyPrice(player, definition, cfg, index, 1)
            if (price <= 0) return null
            out = out.replace("{buy}", formatPrice(price, definition))
        }
        if (out.contains("{sell}")) {
            if (!cfg.sellEnabled) return null
            val price = getSellPrice(player, definition, cfg, index, 1)
            if (price <= 0) return null
            out = out.replace("{sell}", formatPrice(price, definition))
        }
        if (out.contains("{stock}") || out.contains("{stock_max}")) {
            val stock = stockOf(player, definition, index, cfg)
            out = out
                .replace("{stock_max}", cfg.dynamicPricing.stockMax.toString())
                .replace("{stock}", stock.toString())
        }
        return out
            .replace("{currency}", definition.currencySymbol)
            .replace("{item}", baseName)
    }

    /** The item's own display name, re-serialized to MiniMessage so it can be re-injected. */
    private fun baseDisplayName(meta: org.bukkit.inventory.meta.ItemMeta, stack: ItemStack): String =
        meta.displayName()?.let { net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(it) }
            ?: stack.type.name.lowercase().split('_')
                .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    private fun buildShopItem(cfg: ShopItemConfig, index: Int, player: Player, definition: ShopDefinitionEntry): ItemStack {
        val stack = cfg.item.get(player).build(player)
        val meta = stack.itemMeta ?: return stack
        val baseName = baseDisplayName(meta, stack)

        // Empty field => inherit the shop's format; filled field => kept as authored. The shop
        // format never overrides an item's own wording, it only fills the gap.
        val nameTemplate = cfg.name.ifBlank { definition.defaultNameFormat }
        if (nameTemplate.isNotEmpty()) {
            expandItemTokens(applyPlaceholders(player, nameTemplate), player, cfg, index, definition, baseName)
                ?.let { meta.displayName(it.toComponent()) }
        }

        val baseLore = meta.lore()?.toList() ?: emptyList()
        val loreTemplate = cfg.lore.ifEmpty { definition.defaultLoreFormat }
        val customLore = loreTemplate
            .mapNotNull { expandItemTokens(applyPlaceholders(player, it), player, cfg, index, definition, baseName) }
            .flatMap { line -> line.split("\n").map { it.toComponent() } }
        val priceLore = definition.priceLore
            .mapNotNull { expandItemTokens(applyPlaceholders(player, it), player, cfg, index, definition, baseName) }
            .flatMap { line -> line.split("\n").map { it.toComponent() } }

        val lore = baseLore + customLore + priceLore
        if (lore.isNotEmpty()) meta.lore(lore)
        stack.itemMeta = meta
        return stack
    }

    // ──────────────────────────────────────────────
    //  Price Helpers
    // ──────────────────────────────────────────────

    /**
     * Where each of [items] goes, and how many pages that makes. The one place paging is decided:
     * rendering and navigation both read it, so they cannot drift apart.
     *
     * The SHOP_ITEM markers are expanded exactly like every other repeated item — a local copy of
     * this maths used `gap + 1` as the step, ignored `repeatY`, and gated on `count > 1`, which
     * collapsed markers to a single slot whenever the editor left `count` unset (serialized as 0).
     */
    private fun planPage(
        definition: ShopDefinitionEntry,
        items: List<ShopItemConfig>,
    ): ItemPlacement.PagePlan<IndexedValue<ShopItemConfig>> {
        val markers = definition.layoutPool
            .flatMap { it.items }
            .filter(::isShopItemMarker)
            .flatMap { GuiSlotBuilder.expandPositions(it) }
            .map { (x, y) -> ItemPlacement.Cell(x, y) }
        val columns = if (definition.usesTabMode) definition.contentColumns else INVENTORY_COLUMNS
        val rows = if (definition.usesTabMode) definition.contentRows else definition.rows.coerceIn(1, 6)
        // Each article travels with its position in [items]: that position is its stock key, so two
        // identical articles stay two articles with two stocks.
        return ItemPlacement.plan(items.withIndex().toList(), { it.value.guiSlot }, markers, columns, rows)
    }

    /** Shops already reported, so a menu reopened often does not flood the console. */
    private val reportedPlacements: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Says so, once per shop, when articles are moved to free cells or cannot be shown at all. */
    private fun reportPlacement(definition: ShopDefinitionEntry, plan: ItemPlacement.PagePlan<IndexedValue<ShopItemConfig>>) {
        if (!plan.usedFreeCells && plan.unplaced.isEmpty()) return
        if (reportedPlacements.size >= MAX_REPORTED_PLACEMENTS || !reportedPlacements.add(definition.id)) return
        if (plan.unplaced.isNotEmpty()) {
            val names = plan.unplaced.joinToString { "#${it.index} (${articleName(it.value)})" }
            logger.warning(
                "[Shops] Shop '${definition.id}': ${plan.unplaced.size} article(s) cannot be shown, " +
                    "the layout has no free SHOP_ITEM marker and no free cell: $names. " +
                    "Add SHOP_ITEM markers or free a slot."
            )
        } else {
            logger.warning(
                "[Shops] Shop '${definition.id}': every SHOP_ITEM marker is held by an article with a guiSlot, " +
                    "the other articles are placed on the free cells of the content area instead."
            )
        }
    }

    private fun articleName(cfg: ShopItemConfig): String =
        cfg.name.ifBlank { "unnamed article" }

    /** The stock [player] sees: their own in a per-player shop, the shared one otherwise. */
    private fun stockOf(player: Player, definition: ShopDefinitionEntry, index: Int, cfg: ShopItemConfig): Int =
        stockService.getStock(definition.id, index, cfg.dynamicPricing.stockMax, definition.stockOwnerFor(player.uniqueId))

    private fun getBuyPrice(player: Player, definition: ShopDefinitionEntry, cfg: ShopItemConfig, index: Int, amount: Int): Double {
        return when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedBuyPrice * amount
            PriceMode.DYNAMIC -> {
                val stock = stockOf(player, definition, index, cfg)
                priceService.calculateBuyPrice(stock, cfg.dynamicPricing) * amount
            }
            // Zero drops a `{buy}` line, which is the point: a barter shop is expected to use
            // `{item_price}`, the token that says what is actually paid.
            PriceMode.ITEMS -> 0.0
        }
    }

    private fun getSellPrice(player: Player, definition: ShopDefinitionEntry, cfg: ShopItemConfig, index: Int, amount: Int): Double {
        return when (cfg.priceMode) {
            PriceMode.FIXED -> cfg.fixedSellPrice * amount
            PriceMode.DYNAMIC -> {
                val stock = stockOf(player, definition, index, cfg)
                priceService.calculateSellPrice(stock, cfg.dynamicPricing) * amount
            }
            PriceMode.ITEMS -> 0.0
        }
    }
}
