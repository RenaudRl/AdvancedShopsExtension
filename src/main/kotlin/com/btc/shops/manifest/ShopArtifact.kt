package com.btc.shops.manifest

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Tags("shop_artifact")
@Entry("shop_artifact", "Runtime storage for shops", Colors.BLUE, "mdi:database")
class ShopArtifact(
    override val id: String = "",
    override val name: String = "",
    override val artifactId: String = "shops"
) : ArtifactEntry {

    @Serializable
    data class ShopArtifactData(
        val items: MutableMap<String, ShopItemState> = ConcurrentHashMap(),
        val lastResetGlobal: MutableMap<String, Long> = ConcurrentHashMap(),
        val lastResetServer: MutableMap<String, MutableMap<String, Long>> = ConcurrentHashMap()
    )

    @Serializable
    data class ShopItemState(
        var stock: Int = 0,
        var demandBuy: Int = 0,
        var demandSell: Int = 0,
        var priceState: Double = 0.0,
        var lastUpdate: Long = 0L
    )
}
