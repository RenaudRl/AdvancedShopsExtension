package com.btc.shops.service

import com.btc.shops.manifest.ItemCost
import com.btc.shops.manifest.PriceMode
import com.btc.shops.manifest.ShopDefinitionEntry
import com.btc.shops.manifest.ShopItemConfig
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.logger
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player

/**
 * The Bukkit side of a price paid in items: count, refuse, remove.
 *
 * The decisions live in [ItemPriceRule], which needs neither a server nor a player. This service
 * only translates an inventory into counts, then applies what the rule decided.
 */
@Singleton
class ItemPriceService {

    private val plainText = PlainTextComponentSerializer.plainText()

    /** What can happen when an article is to be paid in items. */
    sealed interface Verdict {
        /** The player can pay; [withdrawals] says how much to take from each inventory slot. */
        data class Payable(val withdrawals: Map<Int, Int>) : Verdict

        /** Items are missing; [missing] names them with the quantity that is lacking. */
        data class Missing(val missing: Map<String, Int>) : Verdict

        /**
         * The price means nothing: no line, or a zero quantity.
         *
         * Distinct from [Missing] on purpose: it is not the player's job to understand that an
         * article is misconfigured, and above all this is **not** a free sale.
         */
        data class Invalid(val reason: String) : Verdict
    }

    /**
     * The price in items that applies to [cfg], or `null` when this article is not paid in items.
     *
     * `null` and "empty price" are two different things: the first means "this shop runs on
     * currency", the second is a mistake. Confusing them would make free an article that was
     * meant to be paid for.
     */
    fun applicablePrice(definition: ShopDefinitionEntry, cfg: ShopItemConfig): ItemPriceRule.Resolution<ItemCost>? {
        if (cfg.priceMode != PriceMode.ITEMS) return null
        return ItemPriceRule.resolve(
            override = cfg.itemPrice,
            default = definition.defaultItemPrice,
            quantity = ItemCost::amount,
        )
    }

    /**
     * What buying [quantity] units would cost, without removing anything.
     *
     * The plan is computed **entirely** before a single item moves: a four-ingredient payment that
     * failed on the third would already have consumed the first two, and the player would have
     * paid for nothing.
     */
    fun evaluate(
        player: Player,
        resolution: ItemPriceRule.Resolution<ItemCost>,
        quantity: Int,
    ): Verdict {
        val lines = when (resolution) {
            is ItemPriceRule.Resolution.Absent ->
                return Verdict.Invalid("no price in items is declared, neither on the article nor on the shop")
            is ItemPriceRule.Resolution.InvalidQuantity ->
                return Verdict.Invalid(
                    "price line #${resolution.position + 1} asks for ${resolution.quantity} item(s)"
                )
            is ItemPriceRule.Resolution.Price -> resolution.lines
        }
        if (quantity <= 0) return Verdict.Invalid("bought quantity is zero")

        val contents = player.inventory.contents

        // Simulation: two price lines may target the SAME stacks ("iron" twice, or a catalogue
        // item and its material). Counting each line on its own would add them up on the same
        // stock and let through a payment the inventory does not cover. So consume in a copy,
        // line after line, before touching anything.
        val remaining = HashMap<Int, Int>()
        contents.forEachIndexed { i, stack -> if (stack != null && !stack.type.isAir) remaining[i] = stack.amount }

        val withdrawals = HashMap<Int, Int>()
        val missing = LinkedHashMap<String, Int>()

        lines.forEach { line ->
            val template = line.item.get(player)
            var toTake = line.amount * quantity

            for ((slot, available) in remaining.entries.sortedBy { it.key }) {
                if (toTake <= 0) break
                if (available <= 0) continue
                val stack = contents[slot] ?: continue
                if (!template.isSameAs(player, stack)) continue

                val taken = minOf(available, toTake)
                remaining[slot] = available - taken
                withdrawals[slot] = (withdrawals[slot] ?: 0) + taken
                toTake -= taken
            }

            if (toTake > 0) {
                val name = readableName(player, line)
                missing[name] = (missing[name] ?: 0) + toTake
            }
        }

        return if (missing.isEmpty()) Verdict.Payable(withdrawals) else Verdict.Missing(missing)
    }

    /**
     * The largest number of units the player can pay for, 0 when not even one.
     *
     * Found by bisection over [evaluate] rather than computed line by line, and that is the
     * delicate point: dividing the available stock by each line's price **overestimates** as soon
     * as two lines target the same stacks — each would believe it owns the whole of it. [evaluate]
     * simulates the withdrawal, so only it answers right; the bisection costs a dozen calls, which
     * is nothing next to a player click.
     */
    fun maxAffordableUnits(
        player: Player,
        resolution: ItemPriceRule.Resolution<ItemCost>,
        ceiling: Int,
    ): Int {
        if (ceiling <= 0) return 0
        if (evaluate(player, resolution, 1) !is Verdict.Payable) return 0

        var low = 1
        var high = ceiling
        while (low < high) {
            val middle = low + (high - low + 1) / 2
            if (evaluate(player, resolution, middle) is Verdict.Payable) low = middle else high = middle - 1
        }
        return low
    }

    /**
     * Applies a withdrawal plan produced by [evaluate].
     *
     * Only to be called on a [Verdict.Payable] from the **same** tick: the plan names slots by
     * index, and an inventory changed in between would have something else removed. This is why
     * the call immediately follows the evaluation in the buy handler, with nothing yielding in
     * between.
     */
    fun withdraw(player: Player, withdrawals: Map<Int, Int>) {
        val contents = player.inventory.contents
        withdrawals.forEach { (slot, count) ->
            val stack = contents[slot] ?: return@forEach
            val left = stack.amount - count
            if (left <= 0) player.inventory.setItem(slot, null) else stack.amount = left
        }
    }

    /**
     * The price in items, written to be read — **one line per item, dash-prefixed**:
     *
     * ```
     * - 12 × Copper Ingot
     * - 3 × Kelp
     * ```
     *
     * Backs the `{item_price}` lore token. Without it a barter shop would show "Buy: 0" or nothing
     * at all, and the player would not know what is asked before being refused. Lines rather than
     * a comma list: a five-item price on one lore line stretches the tooltip across the screen.
     */
    fun describe(player: Player, lines: List<ItemCost>): String =
        lines.joinToString("\n") { "- ${it.amount} × ${readableName(player, it)}" }

    /** The name the player reads in the refusal. Without a readable name, a refusal helps no one. */
    private fun readableName(player: Player, line: ItemCost): String = runCatching {
        val stack = line.item.get(player).build(player)
        val name = stack.itemMeta?.displayName()
        if (name != null) plainText.serialize(name) else stack.type.name.lowercase().replace('_', ' ')
    }.getOrElse {
        logger.warning("Could not name a price line paid in items: ${it.message}")
        "unknown item"
    }
}
