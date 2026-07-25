package com.btc.shops.service

import com.btc.shops.manifest.ShopArtifact
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.entry.AssetManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.koin.java.KoinJavaComponent.get
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles persistence for [ShopArtifact] via AssetManager.
 * Contains the cache and load/save logic that cannot live in the Entry itself
 * (KSP forbids stateful entries).
 */
@Singleton
class ShopArtifactPersistenceService {

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
        private const val CACHE_DURATION_MS = 500L
    }

    private var cachedData: ShopArtifact.ShopArtifactData? = null
    private var lastCacheTime: Long = 0

    /**
     * The ShopArtifact entry, or null if not yet registered.
     * Returns null when the extension is still loading or the entry hasn't been created.
     * Callers MUST handle null gracefully — the artifact is loaded lazily.
     */
    private val artifactOrNull: ShopArtifact?
        get() = Query.findById<ShopArtifact>("shop_artifact")
            ?: Query(ShopArtifact::class).findByName("shop_artifact")

    private val artifact: ShopArtifact
        get() = artifactOrNull ?: ShopArtifact("shop_artifact", "Shop Data")

    fun load(): ShopArtifact.ShopArtifactData {
        val now = System.currentTimeMillis()
        if (cachedData != null && now - lastCacheTime < CACHE_DURATION_MS) {
            return cachedData!!
        }

        val assetManager = get<AssetManager>(AssetManager::class.java)
        val exists = runBlocking {
            runCatching { assetManager.containsAsset(this@ShopArtifactPersistenceService.artifact) }.getOrDefault(false)
        }

        val content = if (exists) {
            runCatching {
                runBlocking { assetManager.fetchStringAsset(this@ShopArtifactPersistenceService.artifact) }
            }.getOrNull()
        } else null

        val data = if (content.isNullOrBlank()) {
            ShopArtifact.ShopArtifactData()
        } else {
            runCatching {
                gson.fromJson(content, ShopArtifact.ShopArtifactData::class.java)
            }.getOrDefault(ShopArtifact.ShopArtifactData())
        }

        cachedData = data
        lastCacheTime = now
        return data
    }

    fun save(data: ShopArtifact.ShopArtifactData) {
        val assetManager = get<AssetManager>(AssetManager::class.java)
        val json = gson.toJson(data)
        runBlocking {
            assetManager.storeStringAsset(this@ShopArtifactPersistenceService.artifact, json)
        }
        cachedData = data
        lastCacheTime = System.currentTimeMillis()
    }

    fun update(block: (ShopArtifact.ShopArtifactData) -> Unit) {
        val data = load()
        block(data)
        save(data)
    }
}
