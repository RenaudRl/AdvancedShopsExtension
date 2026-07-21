package com.btc.shops.manifest

import btcrenaud.gui.LayoutData
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.utils.Sound
import kotlinx.serialization.Contextual

/**
 * Declarative definition of a shop and its items.
 *
 * Uses layout pool + mainLayoutId for full declarative control via GUI Extension,
 * using shop_button: prefixed placeholders for navigation and item slots.
 *
 * Sub-menus (buy/sell options) are another layout in the same [layoutPool], picked by [subMenuLayoutId].
 */
@Tags("shop")
@Entry("shop_definition", "Defines a shop", Colors.GREEN, "mdi:cart")
class ShopDefinitionEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Inventory title displayed to players. Supports color codes and placeholders.")
    val title: String = "",

    @Help("Number of inventory rows (1-6). Used to determine inventory size.")
    val rows: Int = 6,
    @Help("Item used to fill empty slots.")
    val fillerItem: Var<Item> = ConstVar(Item.Empty),
    @Help("Lore lines appended to each item to display prices. Placeholders: {buy}, {sell}. Supports color codes and placeholders.")
    val priceLore: List<String> = listOf("<yellow>Buy: {buy}", "<red>Sell: {sell}"),

    @Help("Economy provider used for pricing.")
    val currency: CurrencyType = CurrencyType.VAULT,
    @Help("Placeholder used to fetch player balance when currency is placeholder.")
    val balancePlaceholder: String = "",
    @Help("Command to add currency. {player} and {amount} will be replaced.")
    val addCommand: String = "",
    @Help("Command to remove currency. {player} and {amount} will be replaced.")
    val removeCommand: String = "",

    @Help("Tax percentage applied on transactions (0.0 = no tax, 10.0 = 10% tax).")
    val taxRate: Double = 0.0,
    @Help("How taxes are applied: PER_TRANSACTION (global rate) or PER_ITEM (per-item override via taxRateOverride).")
    val taxMode: TaxMode = TaxMode.PER_TRANSACTION,

    @Help("Policy determining when stock resets. Supports Cron, Interval, Daily, Weekly, and Monthly.")
    val reset: ResetPolicy = ResetPolicy.None(),

    @Help("Items available in the shop.")
    val items: List<ShopItemConfig> = emptyList(),
    @Help("Item pools referenced by this shop. Pool items are merged into the shop display.")
    val itemPools: List<Ref<ShopPoolEntry>> = emptyList(),

    @Help("Layout pool for declarative menu design. Holds every layout this shop uses — the main menu and the buy/sell sub-menu are both selected out of this pool by id.")
    val layoutPool: List<LayoutData> = emptyList(),
    @Help("ID of the main layout within the layout pool to display.")
    val mainLayoutId: String = "",

    @Help("ID of the buy/sell sub-menu layout within the layout pool. Uses shop_button: prefixed placeholders. Leave empty for no sub-menu.")
    val subMenuLayoutId: String = "",
    @Help("Title for the sub-menu. Supports color codes and placeholders. {item_name} placeholder available.")
    val subMenuTitle: String = "<yellow>{item_name} Options",

    @Help("Click-type configuration for all shop interactions. Customize which mouse buttons trigger buy/sell/submenu actions.")
    val interactionConfig: ShopInteractionConfig = ShopInteractionConfig(),

    @Help("Global limit per player across all items in this shop. 0 = unlimited.")
    val globalPlayerLimit: Int = 0,
    @Help("Global buy limit across all items (all players combined). 0 = unlimited.")
    val globalBuyLimit: Int = 0,
    @Help("Global sell limit across all items (all players combined). 0 = unlimited.")
    val globalSellLimit: Int = 0,

    @Help("Default refresh rate in ticks for item displays. 20 = 1 second.")
    val defaultRefreshTicks: Int = 20,

    @Help("If true, each player has their own independent shop state (stock, limits).")
    val perPlayer: Boolean = false,

    @Help("Sound played on successful purchase.")
    val buySound: Sound? = null,
    @Help("Sound played on successful sale.")
    val sellSound: Sound? = null,
    @Help("Sound played when a transaction is denied.")
    val deniedSound: Sound? = null,

    @Help("Messages shown when the information button is clicked. Supports color codes, placeholders and multiline.")
    val infoMessage: List<String> = emptyList(),

    @Help("Message shown when entering a custom buy amount. Supports color codes and placeholders.")
    val buyAmountPrompt: String = "<yellow>Enter amount to buy:",
    @Help("Message shown when entering a custom sell amount. Supports color codes and placeholders.")
    val sellAmountPrompt: String = "<yellow>Enter amount to sell:",
    @Help("Label used for the confirmation button in amount dialogs. Supports color codes and placeholders.")
    val amountConfirmButton: String = "<green>Confirm",
    @Help("Label for the custom amount input. Supports color codes and placeholders.")
    val amountInputLabel: String = "<white>Amount",
    @Help("Placeholder text inside the custom amount input. Supports color codes and placeholders.")
    val amountInputPlaceholder: String = "",
    @Help("Width of the custom amount input field.")
    val amountInputWidth: Int = 4,
    @Help("Maximum allowed length for the custom amount input.")
    val amountInputMaxLength: Int = 10,
    @Help("Message shown when the entered custom amount is not a positive whole number.")
    val invalidAmountMessage: String = "<red>Please enter a valid amount",

    @Help("Message when the player cannot afford a purchase. Placeholders: {amount}, {price}.")
    val cannotAffordMessage: String = "<red>You cannot afford this purchase",
    @Help("Message when the player's inventory is full. Placeholders: {amount}, {price}.")
    val inventoryFullMessage: String = "<red>Not enough inventory space",
    @Help("Message sent after a successful purchase. Placeholders: {amount}, {price}.")
    val purchaseMessage: String = "<green>Purchased x{amount} for {price}",
    @Help("Message when the item cannot be sold. Placeholders: {amount}, {price}.")
    val cannotSellMessage: String = "<red>This item cannot be sold",
    @Help("Message when the player doesn't have enough items to sell. Placeholders: {amount}, {price}.")
    val notEnoughItemsMessage: String = "<red>You don't have enough items to sell",
    @Help("Message sent after a successful sale. Placeholders: {amount}, {price}.")
    val sellMessage: String = "<green>Sold x{amount} for {price}",
    @Help("Message when no economy provider is available.")
    val noEconomyMessage: String = "<red>No economy available",

    @Help("Criteria that must be met to access the shop.")
    val criteria: List<@Contextual Criteria> = emptyList(),
    @Help("Message when the required criteria are not met.")
    val criteriaFailMessage: String = "<red>You cannot access this shop",
    @Help("Item displayed when a shop item is locked.")
    val lockedItem: Var<Item> = ConstVar(Item.Empty),
    @Help("Message when a player reaches the purchase limit for an item.")
    val limitReachedMessage: String = "<red>Purchase limit reached",

    @Help("Price format template. Placeholders: {amount} = formatted price, {currency} = currency symbol. Default: {amount}")
    val priceFormat: String = "{amount}",
    @Help("Currency symbol used in price display. Example: $, €, pts.")
    val currencySymbol: String = "",

    @Help("Artifact used for persistent storage and synchronisation.")
    val artifact: Ref<ShopArtifact> = emptyRef()
) : ManifestEntry {

    /** Whether this entry uses the layout pool mode for the main menu. */
    val usesLayoutPool: Boolean get() = layoutPool.isNotEmpty() && mainLayoutId.isNotBlank()

    /** Whether this entry has a sub-menu layout configured in [layoutPool]. */
    val hasSubMenu: Boolean get() = subMenuLayoutId.isNotBlank() && layoutPool.any { it.id == subMenuLayoutId }
}
