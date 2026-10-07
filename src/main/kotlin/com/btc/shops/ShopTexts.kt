package com.btc.shops

import com.typewritermc.engine.paper.snippets.snippet
import net.kyori.adventure.text.minimessage.MiniMessage

// What the `/tw shop ...` commands answer, the staff ones (`/tw shop admin ...`) included, is a Typewriter snippet: the server
// owner changes it in the snippets file, nothing is fixed in the code. The values below are the defaults a snippet starts from.
// Every reply is MiniMessage and is sent without the Typewriter prefix; the colour of a reply is part of its text. A value the
// reply shows (a shop name, a player) is escaped, so it never acts as a tag. The command errors (unknown shop in the argument)
// are plain text. The texts a player reads while trading are fields of the shop definition, not snippets.

private const val PATH = "content.shop."

private val helpText by snippet(
    PATH + "help",
    "<gold>Shop commands:</gold> <yellow>/tw shop <id></yellow> to open a shop, <yellow>/tw shop admin</yellow> for operator tools.",
    "Answer to /tw shop with no shop. MiniMessage: a tag that is not a known one is shown as written.",
)
private val unknownShopErrorText by snippet(
    PATH + "unknown_shop_error", "Unknown shop: {shop}",
    "Command error when the shop typed is not a shop id. Plain text, no MiniMessage. {shop} is what was typed.",
)

private val adminHelpText by snippet(
    PATH + "admin.help",
    """
    <gold>Shop admin commands:</gold>
    <yellow>/tw shop admin list</yellow> <gray>List loaded shops.</gray>
    <yellow>/tw shop admin info <shop></yellow> <gray>Inspect a definition.</gray>
    <yellow>/tw shop admin open <shop> [target]</yellow> <gray>Open a shop for a player.</gray>
    <yellow>/tw shop admin refresh|close [target]</yellow> <gray>Manage an open session.</gray>
    <yellow>/tw shop admin reset <shop></yellow> <gray>Reset runtime stock and limits.</gray>
    <yellow>/tw shop admin stock <shop> <item> get|set|add|remove [amount]</yellow>
    <yellow>/tw shop admin history [target]</yellow> <gray>Show recent transactions.</gray>
    """.trimIndent(),
    "Answer to /tw shop admin with nothing after it: one line per command, lines separated by a line break. MiniMessage.",
)
private val adminUnknownShopText by snippet(
    PATH + "admin.unknown_shop", "<red>Unknown shop: {shop}",
    "Answer when an admin command names a shop that does not exist. {shop} is what was typed.",
)
private val adminListEmptyText by snippet(
    PATH + "admin.list_empty", "<yellow>No shop definitions are currently loaded.",
    "Answer to /tw shop admin list when no shop is loaded.",
)
private val adminListHeaderText by snippet(
    PATH + "admin.list_header", "<gold>Loaded shops ({count}):",
    "First line of /tw shop admin list. {count} is the number of shops.",
)
private val adminListLineText by snippet(
    PATH + "admin.list_line", "<white>- {id} | {name} | items={items} | pools={pools}",
    "One line per shop in /tw shop admin list. {id} and {name} are the shop's, {items} its direct items, {pools} its item pools.",
)
private val adminOpenedText by snippet(
    PATH + "admin.opened", "<green>Opened {shop} for {player}.",
    "Answer to /tw shop admin open. {shop} is the shop id, {player} the player it was opened for.",
)
private val adminRefreshedText by snippet(
    PATH + "admin.refreshed", "<green>Refreshed the shop session for {player}.",
    "Answer to /tw shop admin refresh. {player} is the player whose shop was redrawn.",
)
private val adminClosedText by snippet(
    PATH + "admin.closed", "<green>Closed the shop session for {player}.",
    "Answer to /tw shop admin close. {player} is the player whose shop was closed.",
)
private val adminResetText by snippet(
    PATH + "admin.reset", "<green>Reset runtime stock and limits for {shop}.",
    "Answer to /tw shop admin reset. {shop} is the shop id.",
)
private val adminHistoryHeaderText by snippet(
    PATH + "admin.history_header", "<gold>Recent transactions for {player} ({count}):",
    "First line of /tw shop admin history. {player} is the player, {count} the number of transactions shown.",
)
private val adminHistoryEmptyText by snippet(
    PATH + "admin.history_empty", "<yellow>No transactions recorded in the current runtime.",
    "Answer to /tw shop admin history when the player has no transaction since the server started.",
)
private val adminHistoryLineText by snippet(
    PATH + "admin.history_line", "<white>{time} | {type} | {shop}[{index}] | amount={amount} | price={price}",
    "One line per transaction in /tw shop admin history. {time} is the date (ISO 8601), {type} BUY or SELL, {shop} the shop id, {index} the item index in the shop, {amount} the quantity, {price} the price paid.",
)
private val adminInfoTitleText by snippet(
    PATH + "admin.info_title", "<gold>=== Shop {shop} ===",
    "First line of /tw shop admin info. {shop} is the shop id.",
)
private val adminInfoNameText by snippet(
    PATH + "admin.info_name", "<white>Name: {name}",
    "Line of /tw shop admin info that names the entry. {name} is the entry name.",
)
private val adminInfoTitleLineText by snippet(
    PATH + "admin.info_inventory_title", "<white>Title: {title}",
    "Line of /tw shop admin info that gives the inventory title. {title} is the Title field of the shop, shown as written.",
)
private val adminInfoCurrencyText by snippet(
    PATH + "admin.info_currency", "<white>Currency: {currency} | rows={rows}",
    "Line of /tw shop admin info about the economy. {currency} is the currency provider, {rows} the rows of the inventory.",
)
private val adminInfoItemsText by snippet(
    PATH + "admin.info_items", "<white>Items: {items} direct | pools={pools} | access criteria={criteria}",
    "Line of /tw shop admin info that counts what the shop holds. {items} is the direct items, {pools} the item pools, {criteria} the access criteria.",
)
private val adminInfoResetText by snippet(
    PATH + "admin.info_reset", "<white>Reset policy: {policy}",
    "Line of /tw shop admin info about the reset. {policy} is the kind of reset policy.",
)
private val adminInfoItemText by snippet(
    PATH + "admin.info_item",
    "<white>[{index}] {name} | buy={buy} sell={sell} | playerLimit={playerLimit} globalLimit={globalLimit} | item criteria={criteria}",
    "One line per direct item in /tw shop admin info. {index} is the item index, {name} its name, {buy} and {sell} whether it can be bought or sold, {playerLimit} and {globalLimit} its limits, {criteria} its criteria.",
)
private val adminInfoUnnamedItemText by snippet(
    PATH + "admin.info_item_unnamed", "<unnamed>",
    "Plain text (no MiniMessage) shown as {name} of /tw shop admin info for a direct item that has no name.",
)
private val adminStockUnlimitedText by snippet(
    PATH + "admin.stock_unlimited", "<yellow>{shop}[{index}] uses unlimited stock.",
    "Answer to /tw shop admin stock on an item that has no stock limit. {shop} is the shop id, {index} the item index.",
)
private val adminStockValueText by snippet(
    PATH + "admin.stock_value", "<green>{shop}[{index}] stock: {stock}/{max}.",
    "Answer to /tw shop admin stock ... get. {shop} is the shop id, {index} the item index, {stock} the stock left, {max} the stock maximum.",
)
private val adminStockOutOfRangeText by snippet(
    PATH + "admin.stock_out_of_range", "<red>Stock must stay between 0 and {max}.",
    "Answer to /tw shop admin stock ... set when the amount is above the stock maximum. {max} is the stock maximum.",
)
private val adminStockUpdatedText by snippet(
    PATH + "admin.stock_updated", "<green>{shop}[{index}] stock set to {stock}/{max}.",
    "Answer to /tw shop admin stock ... set, add or remove. {shop} is the shop id, {index} the item index, {stock} the new stock, {max} the stock maximum.",
)
private val adminUnknownItemText by snippet(
    PATH + "admin.unknown_item", "<red>Unknown direct item index {index} in {shop}.",
    "Answer to /tw shop admin stock when the item index is not a direct item of the shop. {index} is what was typed, {shop} the shop id.",
)

private fun safe(value: String): String = MiniMessage.miniMessage().escapeTags(value)

/** The answers of the shop commands, each a snippet filled with its values. */
internal object ShopTexts {

    /** Loads the snippets (they register when this file loads), so the snippets file lists them as soon as the extension starts. */
    fun register() {
        help()
    }

    fun help() = helpText

    /** Plain text: this one is a command error, not a chat message. */
    fun unknownShopError(shop: String) = ShopTextTemplate.fill(unknownShopErrorText, mapOf("shop" to shop))

    fun adminHelp() = adminHelpText

    fun unknownShop(shop: String) = ShopTextTemplate.fill(adminUnknownShopText, mapOf("shop" to safe(shop)))

    fun listEmpty() = adminListEmptyText

    fun listHeader(count: Int) = ShopTextTemplate.fill(adminListHeaderText, mapOf("count" to "$count"))

    fun listLine(id: String, name: String, items: Int, pools: Int) = ShopTextTemplate.fill(
        adminListLineText,
        mapOf("id" to safe(id), "name" to safe(name), "items" to "$items", "pools" to "$pools"),
    )

    fun opened(shop: String, player: String) =
        ShopTextTemplate.fill(adminOpenedText, mapOf("shop" to safe(shop), "player" to safe(player)))

    fun refreshed(player: String) = ShopTextTemplate.fill(adminRefreshedText, mapOf("player" to safe(player)))

    fun closed(player: String) = ShopTextTemplate.fill(adminClosedText, mapOf("player" to safe(player)))

    fun reset(shop: String) = ShopTextTemplate.fill(adminResetText, mapOf("shop" to safe(shop)))

    fun historyHeader(player: String, count: Int) =
        ShopTextTemplate.fill(adminHistoryHeaderText, mapOf("player" to safe(player), "count" to "$count"))

    fun historyEmpty() = adminHistoryEmptyText

    fun historyLine(time: String, type: String, shop: String, index: Int, amount: Int, price: Double) = ShopTextTemplate.fill(
        adminHistoryLineText,
        mapOf(
            "time" to safe(time), "type" to safe(type), "shop" to safe(shop),
            "index" to "$index", "amount" to "$amount", "price" to "$price",
        ),
    )

    fun infoTitle(shop: String) = ShopTextTemplate.fill(adminInfoTitleText, mapOf("shop" to safe(shop)))

    fun infoName(name: String) = ShopTextTemplate.fill(adminInfoNameText, mapOf("name" to safe(name)))

    fun infoInventoryTitle(title: String) = ShopTextTemplate.fill(adminInfoTitleLineText, mapOf("title" to safe(title)))

    fun infoCurrency(currency: String, rows: Int) =
        ShopTextTemplate.fill(adminInfoCurrencyText, mapOf("currency" to safe(currency), "rows" to "$rows"))

    fun infoItems(items: Int, pools: Int, criteria: Int) = ShopTextTemplate.fill(
        adminInfoItemsText,
        mapOf("items" to "$items", "pools" to "$pools", "criteria" to "$criteria"),
    )

    fun infoReset(policy: String) = ShopTextTemplate.fill(adminInfoResetText, mapOf("policy" to safe(policy)))

    fun infoItem(
        index: Int,
        name: String,
        buy: Boolean,
        sell: Boolean,
        playerLimit: Int,
        globalLimit: Int,
        criteria: Int,
    ) = ShopTextTemplate.fill(
        adminInfoItemText,
        mapOf(
            "index" to "$index",
            "name" to safe(name.ifBlank { adminInfoUnnamedItemText }),
            "buy" to "$buy", "sell" to "$sell",
            "playerLimit" to "$playerLimit", "globalLimit" to "$globalLimit",
            "criteria" to "$criteria",
        ),
    )

    fun stockUnlimited(shop: String, index: Int) =
        ShopTextTemplate.fill(adminStockUnlimitedText, mapOf("shop" to safe(shop), "index" to "$index"))

    fun stockValue(shop: String, index: Int, stock: Int, max: Int) = ShopTextTemplate.fill(
        adminStockValueText,
        mapOf("shop" to safe(shop), "index" to "$index", "stock" to "$stock", "max" to "$max"),
    )

    fun stockOutOfRange(max: Int) = ShopTextTemplate.fill(adminStockOutOfRangeText, mapOf("max" to "$max"))

    fun stockUpdated(shop: String, index: Int, stock: Int, max: Int) = ShopTextTemplate.fill(
        adminStockUpdatedText,
        mapOf("shop" to safe(shop), "index" to "$index", "stock" to "$stock", "max" to "$max"),
    )

    fun unknownItem(shop: String, index: Int) =
        ShopTextTemplate.fill(adminUnknownItemText, mapOf("shop" to safe(shop), "index" to "$index"))
}
