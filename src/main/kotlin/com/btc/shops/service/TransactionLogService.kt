package com.btc.shops.service

import com.btc.shops.manifest.ShopArtifact
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.Singleton
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Singleton
class TransactionLogService {
    companion object {
        private const val MAX_ENTRIES_PER_PLAYER = 50
    }

    private val cache = ConcurrentHashMap<UUID, MutableList<TransactionRecord>>()

    fun log(record: TransactionRecord) {
        val logs = cache.getOrPut(record.playerUuid) { mutableListOf() }
        synchronized(logs) {
            logs.add(0, record)
            if (logs.size > MAX_ENTRIES_PER_PLAYER) {
                logs.removeAt(logs.lastIndex)
            }
        }
    }

    fun getHistory(playerUuid: UUID): List<TransactionRecord> {
        return cache.getOrPut(playerUuid) { mutableListOf() }
    }
}

data class TransactionRecord(
    val playerUuid: UUID,
    val shopId: String,
    val itemIndex: Int,
    val type: String,
    val amount: Int,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis()
)
