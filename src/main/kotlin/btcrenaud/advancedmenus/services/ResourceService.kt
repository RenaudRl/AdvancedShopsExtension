package btcrenaud.advancedmenus.services

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player

class ResourceService {
    
    /**
     * Attempts to get a custom item from supported plugins.
     * @param id The namespaced ID (e.g., "itemsadder:ruby" or "nexo:forest_axe")
     * @return The ItemStack or null if not found.
     */
    fun getItem(id: String): ItemStack? {
        val split = id.split(":", limit = 2)
        if (split.size < 2) return null
        
        val namespace = split[0].lowercase()
        val itemId = split[1]

        return when (namespace) {
            "itemsadder" -> getItemsAdderItem(itemId)
            "nexo" -> getNexoItem(itemId)
            "craftengine" -> getCraftEngineItem(itemId)
            else -> null
        }
    }

    /**
     * Spawns a floating 3D model (Furniture/Display).
     */
    fun spawnModel(id: String, location: org.bukkit.Location): Any? {
        val split = id.split(":", limit = 2)
        if (split.size < 2) return null
        
        val namespace = split[0].lowercase()
        val itemId = split[1]

        return when (namespace) {
            "itemsadder" -> spawnItemsAdderFurniture(itemId, location)
            else -> null
        }
    }

    private fun getItemsAdderItem(itemId: String): ItemStack? {
        return try {
            val customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack")
            val getInstanceMethod = customStackClass.getMethod("getInstance", String::class.java)
            val customStack = getInstanceMethod.invoke(null, itemId) ?: return null
            val getItemStackMethod = customStackClass.getMethod("getItemStack")
            getItemStackMethod.invoke(customStack) as? ItemStack
        } catch (e: Exception) { null }
    }

    private fun spawnItemsAdderFurniture(itemId: String, loc: org.bukkit.Location): Any? {
        return try {
            val furnitureClass = Class.forName("dev.lone.itemsadder.api.CustomFurniture")
            val spawnMethod = furnitureClass.getMethod("spawn", String::class.java, org.bukkit.block.Block::class.java)
            spawnMethod.invoke(null, itemId, loc.block)
        } catch (e: Exception) { null }
    }

    private fun getNexoItem(itemId: String): ItemStack? {
        return try {
            val nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems")
            val getItemMethod = nexoItemsClass.getMethod("getItem", String::class.java)
            getItemMethod.invoke(null, itemId) as? ItemStack
        } catch (e: Exception) { null }
    }

    private fun getCraftEngineItem(itemId: String): ItemStack? {
        return try {
            val craftEngineClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems")
            val getItemMethod = craftEngineClass.getMethod("getItem", String::class.java)
            getItemMethod.invoke(null, itemId) as? ItemStack
        } catch (e: Exception) { null }
    }
}
