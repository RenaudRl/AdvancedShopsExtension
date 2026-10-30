package com.btc.shops.ui

import btcrenaud.gui.editor.api.ButtonTypeContribution
import btcrenaud.gui.editor.api.ButtonTypeInfo
import btcrenaud.gui.editor.api.ButtonTypeRegistry

/**
 * The shop button types as the GUI web editor offers them (OmniGUI 0.21 or later).
 *
 * Derived from [ShopButtonType], the enum [ShopButtonResolverLayout] parses, so the editor lists exactly
 * what the resolver understands. The labels are editor vocabulary, not player text.
 */
internal object ShopButtonTypes {
    const val EXTENSION_ID = "Shops"

    // Entry types whose layout pool holds shop buttons.
    private val menuEntryTypes = setOf("shop_definition", "shop_category", "open_shop")

    val contribution = ButtonTypeContribution(
        extensionId = EXTENSION_ID,
        displayName = "Shops",
        prefix = ShopButtonResolverLayout.PREFIX,
        types = ShopButtonType.entries.map(::infoOf),
        appliesTo = { context -> context.entryType in menuEntryTypes },
    )

    fun register() = ButtonTypeRegistry.register(contribution)

    fun unregister() = ButtonTypeRegistry.unregister(EXTENSION_ID)

    // Exhaustive on purpose: a new ShopButtonType does not compile until it is described here.
    private fun infoOf(type: ShopButtonType): ButtonTypeInfo = when (type) {
        ShopButtonType.SHOP_ITEM ->
            ButtonTypeInfo(type.name, "Shop item", "Dynamic slot filled with a shop item.", repeatable = true, category = "dynamic")
        ShopButtonType.BUY_1 ->
            ButtonTypeInfo(type.name, "Buy", "Buys one unit, or the amount after the colon (BUY_1:10).", parameterized = true, category = "action")
        ShopButtonType.SELL_1 ->
            ButtonTypeInfo(type.name, "Sell", "Sells one unit, or the amount after the colon (SELL_1:10).", parameterized = true, category = "action")
        ShopButtonType.BUY_STACK ->
            ButtonTypeInfo(type.name, "Buy a stack", "Buys one full stack of the item.", category = "action")
        ShopButtonType.SELL_STACK ->
            ButtonTypeInfo(type.name, "Sell a stack", "Sells one full stack of the item.", category = "action")
        ShopButtonType.BUY_MAX ->
            ButtonTypeInfo(type.name, "Buy max", "Buys as many as money, inventory space and stock allow.", category = "action")
        ShopButtonType.SELL_ALL ->
            ButtonTypeInfo(type.name, "Sell all", "Sells every matching item in the inventory.", category = "action")
        ShopButtonType.BUY_SUBMENU ->
            ButtonTypeInfo(type.name, "Buy sub-menu", "Opens the buy sub-menu.", category = "action")
        ShopButtonType.SELL_SUBMENU ->
            ButtonTypeInfo(type.name, "Sell sub-menu", "Opens the sell sub-menu.", category = "action")
        ShopButtonType.BUY_CUSTOM ->
            ButtonTypeInfo(type.name, "Buy custom amount", "Buys an amount the player types.", category = "action")
        ShopButtonType.SELL_CUSTOM ->
            ButtonTypeInfo(type.name, "Sell custom amount", "Sells an amount the player types.", category = "action")
        ShopButtonType.NEXT_PAGE ->
            ButtonTypeInfo(type.name, "Next page", "Goes to the next page.", category = "navigation")
        ShopButtonType.PREV_PAGE ->
            ButtonTypeInfo(type.name, "Previous page", "Goes to the previous page.", category = "navigation")
        ShopButtonType.INFO ->
            ButtonTypeInfo(type.name, "Information", "Shows the shop information.", category = "action")
        ShopButtonType.CLOSE ->
            ButtonTypeInfo(type.name, "Close", "Closes the menu.", category = "navigation")
        ShopButtonType.BACK ->
            ButtonTypeInfo(type.name, "Back", "Returns to the previous menu.", category = "navigation")
    }
}
