package com.btc.shops.api

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

/**
 * Fired once a purchase or a sale has completed: money moved and items changed hands.
 *
 * This is the seam other extensions plug into — a Discord relay, an audit log, an analytics
 * pipeline — without Shops ever knowing they exist. The event is announced, never asked for
 * permission: it is emitted after the transaction, so cancelling it would mean nothing.
 *
 * [notificationWebhookId] carries the id the shop author typed on its definition. Shops does not
 * know what a webhook is and does not resolve it; whichever extension handles Discord does.
 */
class ShopTransactionEvent(
    val player: Player,
    val shopId: String,
    val itemIndex: Int,
    val item: ItemStack,
    val amount: Int,
    val price: Double,
    val isBuy: Boolean,
    val notificationWebhookId: String,
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
