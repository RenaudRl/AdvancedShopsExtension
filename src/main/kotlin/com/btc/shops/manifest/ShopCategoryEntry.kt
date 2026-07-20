package com.btc.shops.manifest

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.entry.ManifestEntry

/**
 * A category/tab within a shop definition.
 * Allows organizing shop items into tabs with custom icons and display names.
 */
@Tags("shop", "category")
@Entry("shop_category", "A category within a shop", Colors.ORANGE, "mdi:tag")
class ShopCategoryEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Display name for this category tab.")
    val displayName: String = "",
    @Help("Icon item for this category tab.")
    val icon: Var<Item> = ConstVar(Item.Empty),
    @Help("Items belonging to this category (by index in the parent shop).")
    val itemIndices: List<Int> = emptyList(),
    @Help("Layout pool ID for this category's item display. If empty, uses the parent shop's main layout.")
    val layoutId: String = ""
) : ManifestEntry
