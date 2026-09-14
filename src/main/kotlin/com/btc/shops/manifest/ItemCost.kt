package com.btc.shops.manifest

import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.utils.item.Item
import kotlinx.serialization.Serializable

/**
 * One line of a price paid in items: "12 ingots", "3 kelp".
 *
 * A full price is a **list** of these lines, on purpose: a barter shop asks for several resources
 * at once, and two articles of the same shop rarely cost the same things. A scalar price cannot
 * say that — which is why [com.btc.shops.api.Economy], which deals in a `Double`, is never
 * consulted for an item paid this way.
 */
@Serializable
data class ItemCost(
    @Help("Item requested as payment. A catalogue item is matched by its identity, not only by its material.")
    val item: Var<Item> = ConstVar(Item.Empty),
    @Help("Quantity requested per unit bought. Zero or negative makes the line invalid and the price is refused.")
    val amount: Int = 1,
)
