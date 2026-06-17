package btcrenaud.advancedmenus.services

import btcrenaud.advancedmenus.api.*
import btcrenaud.advancedmenus.util.toARGB
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.util.Quaternion4f
import com.typewritermc.engine.paper.extensions.packetevents.meta
import com.typewritermc.engine.paper.extensions.packetevents.toPacketItem
import com.typewritermc.engine.paper.utils.toPacketLocation
import me.tofaa.entitylib.EntityLib
import me.tofaa.entitylib.meta.Metadata
import me.tofaa.entitylib.meta.display.TextDisplayMeta
import me.tofaa.entitylib.meta.display.ItemDisplayMeta
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta
import me.tofaa.entitylib.wrapper.WrapperEntity
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service responsible for spawning and managing 3D Text and Item displays for menus.
 */
class TextDisplayService {
    private val activeDisplays = ConcurrentHashMap<UUID, MutableList<WrapperEntity>>()
    private val activeCursors = ConcurrentHashMap<UUID, WrapperEntity>()
    internal val entityOriginalPositions = ConcurrentHashMap<Int, Location>()

    data class TextDisplaySettings(
        val content: String,
        val scale: Double = 1.0,
        val globalScale: Double = 1.0,
        val faceCamera: Boolean = false,
        val backgroundColor: com.typewritermc.engine.paper.utils.Color = com.typewritermc.engine.paper.utils.Color.BLACK_BACKGROUND,
        val backgroundAlpha: Double = 0.0,
        val textOpacity: Double = 1.0,
        val lineWidth: Int = 200,
        val alignment: TextAlignment = TextAlignment.CENTER,
        val shadow: Boolean = false,
        val seeThroughBlocks: Boolean = false,
        val brightnessBlock: Int = 15,
        val brightnessSky: Int = 15
    )

    fun spawnButton(
        player: Player,
        location: Location,
        config: Any,
        globalScale: Double,
        textOverride: String? = null
    ): WrapperEntity {
        return when (config) {
            is TextMenuButton -> spawnTextButton(player, location, textOverride ?: config.content, config, globalScale)
            is ItemMenuButton -> spawnItemButton(player, location, config.item.get(player), config.scale, globalScale, config.faceCamera, config.rotationX.toFloat(), config.rotationY.toFloat(), config.rotationZ.toFloat())
            is InputMenuButton -> spawnTextButton(player, location, textOverride ?: config.placeholder, TextMenuButton(
                content = textOverride ?: config.placeholder,
                scale = config.scale,
                faceCamera = config.faceCamera,
                backgroundColor = config.backgroundColor,
                backgroundAlpha = config.backgroundAlpha
            ), globalScale)
            else -> throw IllegalArgumentException("Unsupported button config type: ${config::class.java.name}")
        }
    }

    fun spawnTextButton(
        player: Player,
        location: Location,
        text: String,
        settings: TextMenuButton,
        globalScale: Double
    ): WrapperEntity {
        val entity = WrapperEntity(com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.TEXT_DISPLAY)
        
        entity.meta<TextDisplayMeta> {
            this.text = parseRichText(text)
            
            billboardConstraints = if (settings.faceCamera) {
                AbstractDisplayMeta.BillboardConstraints.CENTER
            } else {
                AbstractDisplayMeta.BillboardConstraints.FIXED
            }

            backgroundColor = settings.backgroundColor.toARGB(settings.backgroundAlpha)
            textOpacity = (settings.textOpacity * 255).toInt().toByte()
            
            lineWidth = settings.lineWidth
            isSeeThrough = settings.seeThroughBlocks
            isShadow = settings.shadow
            
            (this as me.tofaa.entitylib.meta.EntityMeta).metadata.setUnambiguous(14.toByte(), settings.alignment.toPacketAlignment() as Any)
            
            val sVal = (settings.scale * globalScale).toFloat()
            scale = Vector3f(sVal, sVal, sVal)
        }

        entity.spawn(location.toPacketLocation())
        entity.addViewer(player.uniqueId)
        
        activeDisplays.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(entity)
        entityOriginalPositions[entity.entityId] = location.clone()
        entity.setBrightness(settings.brightnessBlock, settings.brightnessSky)
        
        return entity
    }

    fun spawnLabel(player: Player, location: Location, settings: TextDisplaySettings): WrapperEntity {
        val entity = WrapperEntity(com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.TEXT_DISPLAY)
        
        entity.meta<TextDisplayMeta> {
            this.text = parseRichText(settings.content)
            billboardConstraints = if (settings.faceCamera) AbstractDisplayMeta.BillboardConstraints.CENTER else AbstractDisplayMeta.BillboardConstraints.FIXED
            backgroundColor = settings.backgroundColor.toARGB(settings.backgroundAlpha)
            textOpacity = (settings.textOpacity * 255).toInt().toByte()
            lineWidth = settings.lineWidth
            isSeeThrough = settings.seeThroughBlocks
            isShadow = settings.shadow
            
            (this as me.tofaa.entitylib.meta.EntityMeta).metadata.setUnambiguous(14.toByte(), settings.alignment.toPacketAlignment() as Any)
            
            val sVal = (settings.scale * settings.globalScale).toFloat()
            scale = Vector3f(sVal, sVal, sVal)
        }

        entity.spawn(location.toPacketLocation())
        entity.addViewer(player.uniqueId)
        activeDisplays.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(entity)
        entityOriginalPositions[entity.entityId] = location.clone()
        entity.setBrightness(settings.brightnessBlock, settings.brightnessSky)
        return entity
    }

    fun spawnItemButton(
        player: Player,
        location: Location,
        item: com.typewritermc.engine.paper.utils.item.Item,
        scaleValue: Double,
        globalScale: Double,
        faceCamera: Boolean,
        rotX: Float = 0f,
        rotY: Float = 0f,
        rotZ: Float = 0f
    ): WrapperEntity {
        val entity = WrapperEntity(com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.ITEM_DISPLAY)
        
        entity.meta<ItemDisplayMeta> {
            this.item = item.build(player).toPacketItem()
            billboardConstraints = if (faceCamera) AbstractDisplayMeta.BillboardConstraints.CENTER else AbstractDisplayMeta.BillboardConstraints.FIXED
            
            val sVal = (scaleValue * globalScale).toFloat()
            scale = Vector3f(sVal, sVal, sVal)
            
            // Apply static rotation
            leftRotation = Quaternion4f(rotX, rotY, rotZ, 1.0f) 
        }

        entity.spawn(location.toPacketLocation())
        entity.addViewer(player.uniqueId)
        activeDisplays.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(entity)
        entityOriginalPositions[entity.entityId] = location.clone()
        entity.setBrightness(15, 15)
        return entity
    }

    /**
     * Updates the opacity of an entity. 
     * For TextDisplay, it adjusts the textOpacity metadata.
     * For ItemDisplay, it currently toggles visibility (as there is no native alpha).
     */
    fun setOpacity(entity: WrapperEntity, opacity: Double) {
        if (entity.getEntityType() == com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.TEXT_DISPLAY) {
            entity.meta<TextDisplayMeta> {
                textOpacity = (opacity * 255).toInt().toByte()
            }
        } else {
            // Item displays don't support alpha, so we just hide/show
            entity.meta<ItemDisplayMeta> {
                isInvisible = opacity < 0.5
            }
        }
    }

    fun updateLocation(entity: WrapperEntity, location: Location) {
        entity.teleport(location.toPacketLocation())
        entityOriginalPositions[entity.entityId] = location.clone()
    }

    fun applyHoverEffects(entity: WrapperEntity, effects: List<HoverEffect>, baseScale: Double, screenScale: Double, hovered: Boolean) {
        val targetScale = if (hovered) {
             val scaleEffect = effects.find { it.type == HoverEffectType.SCALE }
             scaleEffect?.targetScale ?: 1.15
        } else 1.0

        val finalScale = (baseScale * screenScale * targetScale).toFloat()
        
        if (entity.getEntityType() == com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.TEXT_DISPLAY) {
            entity.meta<TextDisplayMeta> {
                scale = Vector3f(finalScale, finalScale, finalScale)
            }
        } else if (entity.getEntityType() == com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.ITEM_DISPLAY) {
            entity.meta<ItemDisplayMeta> {
                scale = Vector3f(finalScale, finalScale, finalScale)
            }
        }
    }

    fun getAlignmentOffsetMultiplier(alignment: TextAlignment): Double = when (alignment) {
        TextAlignment.LEFT -> 0.5
        TextAlignment.RIGHT -> -0.5
        TextAlignment.CENTER -> 0.0
    }

    /**
     * Directly sets the absolute scale of an entity.
     * Used to animate linked decorations on button hover.
     */
    fun setEntityScale(entity: WrapperEntity, scale: Float) {
        if (entity.getEntityType() == com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.TEXT_DISPLAY) {
            entity.meta<TextDisplayMeta> {
                this.scale = Vector3f(scale, scale, scale)
            }
        } else if (entity.getEntityType() == com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.ITEM_DISPLAY) {
            entity.meta<ItemDisplayMeta> {
                this.scale = Vector3f(scale, scale, scale)
            }
        }
    }

    fun clearAll() {
        activeDisplays.forEach { (uuid, list) ->
            list.forEach { it.despawn(); it.remove() }
        }
        activeDisplays.clear()
        activeCursors.forEach { (uuid, entity) ->
            entity.despawn(); entity.remove()
        }
        activeCursors.clear()
        entityOriginalPositions.clear()
    }

    fun removePlayerLabels(player: Player) {
        val displays = activeDisplays.remove(player.uniqueId)
        displays?.forEach { entity ->
            entityOriginalPositions.remove(entity.entityId)
            entity.removeViewer(player.uniqueId)
            entity.despawn()
            entity.remove()
        }
        
        val cursor = activeCursors.remove(player.uniqueId)
        cursor?.let { entity ->
            entity.removeViewer(player.uniqueId)
            entity.despawn()
            entity.remove()
        }
    }

    fun spawnCursor(player: Player, item: com.typewritermc.engine.paper.utils.item.Item, location: Location, globalScale: Double, cursorScale: Double): WrapperEntity {
        val entity = WrapperEntity(com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.ITEM_DISPLAY)
        
        entity.meta<ItemDisplayMeta> {
            this.item = item.build(player).toPacketItem()
            billboardConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
            val sVal = (0.05f * cursorScale * globalScale).toFloat()
            scale = Vector3f(sVal, sVal, sVal)
            leftRotation = Quaternion4f(0f, 0f, 0f, 1f)
            positionRotationInterpolationDuration = 0
        }
        
        entity.spawn(location.toPacketLocation())
        entity.addViewer(player.uniqueId)
        entity.setBrightness(15, 15)

        activeCursors[player.uniqueId] = entity
        return entity
    }

    fun getCursor(player: Player): WrapperEntity? = activeCursors[player.uniqueId]

    fun updateInfoArea(player: Player, entity: WrapperEntity?, content: String) {
        val ent = entity ?: return
        ent.meta<TextDisplayMeta> {
            text = parseRichText(content)
            isInvisible = false
        }
        ent.addViewer(player.uniqueId)
    }

    fun hideInfoArea(player: Player, entity: WrapperEntity?) {
        val ent = entity ?: return
        ent.meta<TextDisplayMeta> {
            text = parseRichText("")
            isInvisible = true
        }
    }

    private fun parseRichText(text: String): net.kyori.adventure.text.Component {
        return try {
            // Use CraftEngine's rich parsing if available via reflection to avoid compile issues
            val helperClass = Class.forName("net.momirealms.craftengine.core.util.AdventureHelper")
            val method = helperClass.getMethod("parseMiniMessage", String::class.java)
            method.invoke(null, text) as net.kyori.adventure.text.Component
        } catch (e: Throwable) {
            // Fallback to standard MiniMessage
            MiniMessage.miniMessage().deserialize(text)
        }
    }

    internal fun WrapperEntity.setBrightness(block: Int, sky: Int) {
        val packed = (sky shl 4) or block
        val finalPacked: Any = packed
        this.meta<me.tofaa.entitylib.meta.EntityMeta>(editor = {
            this.metadata.setUnambiguous(9.toByte(), finalPacked)
        })
    }

    private fun TextAlignment.toPacketAlignment(): Byte = when (this) {
        TextAlignment.LEFT -> 0.toByte()
        TextAlignment.RIGHT -> 1.toByte()
        TextAlignment.CENTER -> 2.toByte()
    }

}
    
internal fun me.tofaa.entitylib.meta.Metadata.setUnambiguous(index: Byte, value: Any) {
    try {
        val method = javaClass.getMethod("set", Byte::class.javaPrimitiveType, Any::class.java, Boolean::class.javaPrimitiveType)
        method.invoke(this, index, value, true)
    } catch (e: Exception) {
        // Fallback or log if needed
    }
}
