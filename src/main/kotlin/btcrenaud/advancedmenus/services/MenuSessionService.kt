package btcrenaud.advancedmenus.services

import btcrenaud.advancedmenus.api.*
import btcrenaud.advancedmenus.entries.manifest.ExtensionConfigManifestEntry
import btcrenaud.advancedmenus.util.toARGB
import btcrenaud.advancedmenus.util.lerp
import btcrenaud.advancedmenus.util.CameraBasis
import com.typewritermc.engine.paper.entry.matches
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.github.retrooper.packetevents.protocol.player.DiggingAction
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging
import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.client.*
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage
import net.kyori.adventure.text.Component
import com.github.retrooper.packetevents.protocol.player.Equipment
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot
import com.typewritermc.engine.paper.extensions.packetevents.toPacketItem
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import com.typewritermc.engine.paper.extensions.packetevents.meta
import com.typewritermc.engine.paper.utils.toPacketLocation
import com.typewritermc.engine.paper.interaction.*
import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.core.interaction.context
import me.tofaa.entitylib.meta.display.*
import me.tofaa.entitylib.meta.Metadata
import me.tofaa.entitylib.wrapper.WrapperEntity
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.bukkit.plugin.Plugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import io.papermc.paper.dialog.Dialog
import com.typewritermc.engine.paper.utils.asMini
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.event.ClickCallback
import com.typewritermc.engine.paper.utils.item.CustomItem
import com.typewritermc.engine.paper.utils.item.components.ItemMaterialComponent
import com.typewritermc.engine.paper.utils.item.components.ItemCustomModelDataComponent
import com.typewritermc.engine.paper.utils.item.components.customModelDataTypes.LegacyCustomModelData
import com.typewritermc.engine.paper.entry.entries.ConstVar
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages 3D menu sessions, input interception, and interaction logic.
 */
class MenuSessionService(private val plugin: Plugin, private val textDisplayService: TextDisplayService) : Listener {

    data class ButtonInfo(
        val name: String,
        val enabled: Boolean = true,
        val screenX: Double,
        val screenY: Double,
        val width: Double,
        val height: Double,
        val priority: Int = 0,
        var entity: WrapperEntity? = null,
        val config: Any, // Can be TextMenuButton or ItemMenuButton
        var hovered: Boolean = false,
        var isInput: Boolean = false,
        var inputPlaceholder: String = "",
        var currentValue: String = "", // For InputMenuButton
        val actions: List<com.typewritermc.core.entries.Ref<com.typewritermc.engine.paper.entry.entries.ActionEntry>> = emptyList(),
        val rightClickActions: List<com.typewritermc.core.entries.Ref<com.typewritermc.engine.paper.entry.entries.ActionEntry>> = emptyList(),
        val baseScale: Double = 1.0,
        val hoverSound: com.typewritermc.engine.paper.utils.Sound = com.typewritermc.engine.paper.utils.Sound.EMPTY,
        val clickSound: com.typewritermc.engine.paper.utils.Sound = com.typewritermc.engine.paper.utils.Sound.EMPTY,
        val stopMenu: StopMenuSettings = StopMenuSettings(),
        val lore: String = "",
        val isLocked: Boolean = false,
        /** Optional content to display in the global InfoArea when this button is hovered. */
        val infoAreaContent: String? = null,
        /** Optional programmatic click callback for direct navigation (used by BTCSky etc.) */
        @Transient
        val onClickCallback: ((Player) -> Unit)? = null,
        /** Optional programmatic right-click callback for context menus (used by BTCSky etc.) */
        @Transient
        val onRightClickCallback: ((Player) -> Unit)? = null,
        /** Optional callback invoked when the player submits a chat input for this button. */
        @Transient
        val onInputCallback: ((Player, String) -> Unit)? = null,
        /** Depth offset from the main interaction plane (positive = closer to camera). */
        val depthOffset: Double = 0.0,
        /** Static rotation overrides. */
        val rotationX: Float = 0f,
        val rotationY: Float = 0f,
        val rotationZ: Float = 0f
    )

    data class Session(
        val player: Player,
        val menuId: String,
        val buttons: MutableList<ButtonInfo>,
        val decorations: MutableList<WrapperEntity> = mutableListOf(),
        val decorationConfigs: List<DecorationElement> = emptyList(),
        val layers: MutableList<WrapperEntity> = mutableListOf(),
        val originalLocation: Location,
        val cameraLocation: Location,
        val closeOnMove: Boolean,
        val screenScale: Double,
        var forwardDistance: Double,
        var cursorSensitivity: Double,
        var cursorSmoothing: Double,
        var playerFov: Double,
        val openActions: List<com.typewritermc.core.entries.Ref<com.typewritermc.engine.paper.entry.entries.ActionEntry>> = emptyList(),
        val closeActions: List<com.typewritermc.core.entries.Ref<com.typewritermc.engine.paper.entry.entries.ActionEntry>> = emptyList(),
        val safeMode: Boolean,
        val lockMovement: Boolean = true,
        val infoAreaEntity: WrapperEntity?,
        val cursorScale: Double = 1.0,
        /** Original screen.forwardDistance before FOV adjustment — needed for live FOV recalculation */
        val baseForwardDistance: Double = 1.0,
        
        var cameraEntity: WrapperEntity? = null,
        var seatEntity: WrapperEntity? = null,
        var cursorEntity: WrapperEntity? = null,
        val linkEntities: MutableList<WrapperEntity> = mutableListOf(),
        val linkArrowEntities: MutableList<WrapperEntity> = mutableListOf(),
        val linkConfigs: List<SkillTreeLink> = emptyList(),

        var cursorXMin: Double = 0.0,
        var cursorXMax: Double = 100.0,
        var cursorYMin: Double = 0.0,
        var cursorYMax: Double = 100.0,
        var cursorItem: com.typewritermc.engine.paper.utils.item.Item? = null,
        var fovCalibration: btcrenaud.advancedmenus.util.CameraBasis.Companion.CalibrationParams = btcrenaud.advancedmenus.util.CameraBasis.Companion.CalibrationParams(),
        var basis: CameraBasis? = null,
        var spawnYaw: Float = 0f,
        var spawnPitch: Float = 0f,
        var cursorX: Double = 50.0,
        var cursorY: Double = 50.0,
        var targetCursorX: Double = 50.0,
        var targetCursorY: Double = 50.0,
        var activeInput: ButtonInfo? = null,
        var hasMoved: Boolean = false,

        var initialX: Double? = null,
        var initialY: Double? = null,
        val creationTime: Long = System.currentTimeMillis(),
        var lastClickTime: Long = 0L,
        var lastPlayerYaw: Float? = null,
        var lastPlayerPitch: Float? = null,
        val hideChat: Boolean = true,
        var lastHeldSlot: Int = -1,
        var scrollY: Double = 0.0,
        var targetScrollY: Double = 0.0,
        var scrollX: Double = 0.0,
        var targetScrollX: Double = 0.0,
        var zoomLevel: Double = 1.0,
        var targetZoomLevel: Double = 1.0,
        var zoomPivotX: Double = 50.0,
        var zoomPivotY: Double = 50.0,
        var wheelConfig: btcrenaud.advancedmenus.api.WheelInteractionConfig = btcrenaud.advancedmenus.api.WheelInteractionConfig(),
        var basePositions: Map<Int, Location> = emptyMap(), // map of entity.entityId to base world position
        @Volatile var isHidingEquipment: Boolean = false,
        
        // Panorama state
        var panoramaYaw: Float = 0f,
        var panoramaPitch: Float = 0f,
        var grabPanorama: Boolean = false,
        var yawSync: Boolean = false,
        var loop: Boolean = false,
        var lastPanoramaX: Double? = null,
        var lastPanoramaY: Double? = null,
        // Invisibility
        val invisibilityMode: btcrenaud.advancedmenus.api.InvisibilityMode = btcrenaud.advancedmenus.api.InvisibilityMode.GLOBAL_DEFAULT,
        // Zone State
        val scrollPagesX: MutableMap<String, Int> = mutableMapOf(),
        val scrollPagesY: MutableMap<String, Int> = mutableMapOf(),
        val zoomFactors: MutableMap<String, Double> = mutableMapOf(),
        val targetZoomFactors: MutableMap<String, Double> = mutableMapOf(),
        // Brightness overrides
        val brightnessBlock: Int? = null,
        val brightnessSky: Int? = null,
        @Volatile var isHidingUI: Boolean = false,
        var instantTransform: Boolean = false,
        val debug: Boolean = false
    )

    private val sessions = ConcurrentHashMap<UUID, Session>()
    private val gracePeriods = ConcurrentHashMap<UUID, Long>()
    private var packetListener: PacketListenerAbstract? = null
    private var tickTask: ScheduledTask? = null

    // No longer registering in init to avoid injection races during player join
    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { _ ->
            tickSessions()
        }, 1L, 1L)
    }

    fun start() {
        if (packetListener == null) {
            registerPacketListeners()
            // CRITICAL FIX: The service must be registered as a Bukkit listener 
            // to enable BlockBreakEvent/BlockDamageEvent cancellation.
            Bukkit.getPluginManager().registerEvents(this, plugin)
        }
    }

    fun shutdown() {
        // 1. Unregister PacketListener
        packetListener?.let {
            PacketEvents.getAPI().eventManager.unregisterListener(it)
            packetListener = null
        }
        
        // 2. Cancel Tick Task
        tickTask?.cancel()
        tickTask = null
        
        // 3. Clear all active sessions
        sessions.forEach { (uuid, _) ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null) stopSession(player)
        }
        sessions.clear()
        gracePeriods.clear()
    }

    fun stop() {
        shutdown()
    }

    private fun tickSessions() {
        sessions.forEach { (_, session) ->
            val player = session.player
            if (!player.isOnline) {
                Bukkit.getRegionScheduler().run(plugin, session.cameraLocation) { stopSession(player) }
                return@forEach
            }

            // Position updates are now primary in subTickCursorUpdate (packet-driven)
            // or handled below if smoothing is needed.
            if (session.cursorSmoothing < 1.0) {
                val t = session.cursorSmoothing.coerceIn(0.01, 1.0)
                session.cursorX = lerp(session.cursorX, session.targetCursorX, t)
                session.cursorY = lerp(session.cursorY, session.targetCursorY, t)
            } else {
                session.cursorX = session.targetCursorX
                session.cursorY = session.targetCursorY
            }

            // --- Task 7: Synchronize Linked Decoration Positions ---
            syncLinkedDecorations(session)

            updateCursorEntity(player, session)
            performHoverDetection(player, session)
            subTickTransforms(session)
            tickStates(session)
            
            if (session.debug) {
                renderDebugHitboxes(session)
            }
            
            // Render Skill Tree Links
            renderLinks(session)
        }
        
        val now = System.currentTimeMillis()
        gracePeriods.entries.removeIf { it.value < now }
    }

    private fun tickStates(session: Session) {
        val player = session.player
        val context = com.typewritermc.core.interaction.context { }
        
        session.buttons.forEach { button ->
            val states = when (val cfg = button.config) {
                is btcrenaud.advancedmenus.api.TextMenuButton -> cfg.states
                is btcrenaud.advancedmenus.api.ItemMenuButton -> cfg.states
                else -> emptyList()
            }
            if (states.isEmpty()) return@forEach
            
            val bestState = states.filter { it.criteria.matches(player, context) }
                                  .maxByOrNull { it.priority }
            
            // Apply state (simplified for now: content and enabled)
            bestState?.let { state ->
                val entity = button.entity ?: return@let
                if (state.content != null && entity.getEntityType() == com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.TEXT_DISPLAY) {
                    val resolved = state.content.parsePlaceholders(player)
                    entity.meta<TextDisplayMeta> {
                        text = MiniMessage.miniMessage().deserialize(resolved)
                    }
                }
            }
        }
    }

    private fun subTickTransforms(session: Session) {
        val t = session.cursorSmoothing.coerceIn(0.05, 1.0)
        var needsUpdate = false
        
        val isInstant = session.instantTransform || session.wheelConfig.instantTransform

        if (Math.abs(session.scrollY - session.targetScrollY) > 0.001 || Math.abs(session.scrollX - session.targetScrollX) > 0.001 || Math.abs(session.zoomLevel - session.targetZoomLevel) > 0.0001) {
            if (isInstant) {
                session.scrollY = session.targetScrollY
                session.scrollX = session.targetScrollX
                session.zoomLevel = session.targetZoomLevel
                session.instantTransform = false // Reset after application
            } else {
                session.scrollY = lerp(session.scrollY, session.targetScrollY, t)
                session.scrollX = lerp(session.scrollX, session.targetScrollX, t)
                session.zoomLevel = lerp(session.zoomLevel, session.targetZoomLevel, t)
            }
            needsUpdate = true
        }

        // Interpolate localized zoom factors
        session.targetZoomFactors.forEach { (id, target) ->
            val current = session.zoomFactors[id] ?: 1.0
            if (Math.abs(current - target) > 0.0001) {
                if (isInstant) {
                    session.zoomFactors[id] = target
                } else {
                    session.zoomFactors[id] = lerp(current, target, t)
                }
                needsUpdate = true
            }
        }

        if (needsUpdate) {
            applyTransforms(session, true)
        }
    }

    private fun applyTransforms(session: Session, needsUpdate: Boolean = false) {
        val basis = session.basis ?: return
        val playerFov = session.playerFov
        val isHiding = session.isHidingUI
        
        val config = ExtensionConfigManifestEntry.getInstance()
        val defaultBlock = config?.defaultBrightnessBlock ?: 15
        val defaultSky = config?.defaultBrightnessSky ?: 15
        val screenBlock = session.brightnessBlock ?: defaultBlock
        val screenSky = session.brightnessSky ?: defaultSky

        session.buttons.forEach { button ->
            val entity = button.entity ?: return@forEach
            
            // 1. Scroll Zone Logic
            var effectiveX = button.screenX
            var effectiveY = button.screenY
            var opacity = if (isHiding) 0.0 else 1.0
            
            val scrollZone = session.wheelConfig.scrollZones.find { zone ->
                button.screenX >= zone.anchorX && button.screenX <= zone.anchorX + zone.width &&
                button.screenY >= zone.anchorY && button.screenY <= zone.anchorY + zone.height
            }
            
            if (scrollZone != null) {
                val pageX = session.scrollPagesX[scrollZone.id] ?: 0
                val pageY = session.scrollPagesY[scrollZone.id] ?: 0
                
                effectiveX -= (pageX * scrollZone.width)
                effectiveY -= (pageY * scrollZone.height)
                
                // Masking & Fade-out
                if (scrollZone.fadeOut && !isHiding) {
                    val margin = 5.0 // Buffer for fade
                    val distLeft = effectiveX - scrollZone.anchorX
                    val distRight = (scrollZone.anchorX + scrollZone.width) - effectiveX
                    val distTop = effectiveY - scrollZone.anchorY
                    val distBottom = (scrollZone.anchorY + scrollZone.height) - effectiveY
                    
                    opacity = Math.min(
                        Math.min(distLeft, distRight),
                        Math.min(distTop, distBottom)
                    ).coerceIn(0.0, margin) / margin
                } else if (!isHiding && (effectiveX < scrollZone.anchorX || effectiveX > scrollZone.anchorX + scrollZone.width ||
                           effectiveY < scrollZone.anchorY || effectiveY > scrollZone.anchorY + scrollZone.height)) {
                    opacity = 0.0
                }
            } else {
                effectiveX += session.scrollX
                effectiveY += session.scrollY
            }

            // Frustum Culling: If completely off-screen, hide and skip expensive world-space calculation
            if (opacity > 0.0 && (effectiveX < -25.0 || effectiveX > 125.0 || effectiveY < -25.0 || effectiveY > 125.0)) {
                opacity = 0.0
            }

            if (opacity <= 0.0) {
                textDisplayService.setOpacity(entity, 0.0)
                return@forEach
            }

            // 2. Zoom Zone Logic
            var zoomFactor = session.zoomLevel
            val zoomZone = session.wheelConfig.zoomZones.find { zone ->
                if (zone.isAbsolute) return@find true
                val dx = button.screenX - zone.centerX
                val dy = button.screenY - zone.centerY
                (dx * dx + dy * dy) <= zone.radius * zone.radius
            }
            
            if (zoomZone != null) {
                zoomFactor *= session.zoomFactors[zoomZone.id] ?: 1.0
            }

            // 3. Layer-Based Zoom Isolation (Sidebar on Layer 5 is static)
            val isSidebar = button.priority == 5 || button.name.startsWith("scroll_") || button.name.startsWith("back")
            val effectiveZoom = if (isSidebar) 1.0 else (zoomFactor * session.screenScale)

            val appliedScale = (button.baseScale * effectiveZoom).toFloat()
            val interpolationDur = if (needsUpdate) 2 else 0
            
            if (entity.getEntityType() == com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.TEXT_DISPLAY) {
                entity.meta<TextDisplayMeta> { 
                    scale = Vector3f(appliedScale, appliedScale, appliedScale)
                    // interpolation_duration (index 9 for Displays)
                    metadata.setUnambiguous(9.toByte(), interpolationDur as Any)
                }
            } else if (entity.getEntityType() == com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.ITEM_DISPLAY) {
                entity.meta<ItemDisplayMeta> { 
                    scale = Vector3f(appliedScale, appliedScale, appliedScale)
                    // interpolation_duration (index 9 for Displays)
                    metadata.setUnambiguous(9.toByte(), interpolationDur as Any)
                }
            }

            // 4. Opacity Application
            textDisplayService.setOpacity(entity, opacity)

            // 5. Brightness Application
            val cfg = button.config
            val bBlock = when (cfg) {
                is TextMenuButton -> if (cfg.brightnessBlock == 15) screenBlock else cfg.brightnessBlock
                is ItemMenuButton -> if (cfg.brightnessBlock == 15) screenBlock else cfg.brightnessBlock
                is MenuLayer -> if (cfg.brightnessBlock == 15) screenBlock else cfg.brightnessBlock
                else -> screenBlock
            }
            val bSky = when (cfg) {
                is TextMenuButton -> if (cfg.brightnessSky == 15) screenSky else cfg.brightnessSky
                is ItemMenuButton -> if (cfg.brightnessSky == 15) screenSky else cfg.brightnessSky
                is MenuLayer -> if (cfg.brightnessSky == 15) screenSky else cfg.brightnessSky
                else -> screenSky
            }
            with(textDisplayService) {
                entity.setBrightness(bBlock, bSky)
            }

            // 6. Coordinate Translation & Cursor-Centric Scaling
            // Factor 1.0 = Default view. 
            // In a grid (Expansion), we want items to spread out relative to the cursor (Zoom goal)
            // Sidebar and navigation buttons stay in their absolute screen positions.
            val pivotX = if (isSidebar) 50.0 else session.cursorX
            val pivotY = if (isSidebar) 42.5 else session.cursorY 
            
            val relX = effectiveX - pivotX
            val relY = effectiveY - pivotY
            
            // Apply scale-space displacement to coordinates
            // This spreads elements away from the pivot as scale increases
            val zoomedX = pivotX + (relX * effectiveZoom)
            val zoomedY = pivotY + (relY * effectiveZoom)
            
            val fwd = session.baseForwardDistance * (1.0 / effectiveZoom)
            val worldPos = CameraBasis.anchorToWorld(zoomedX, zoomedY, 0.0, 0.0, playerFov, fwd)
            
            val btnFwd = fwd - (button.priority * 0.01) + button.depthOffset

            val buttonLoc = basis.screenToWorld(session.cameraLocation, worldPos.x, worldPos.y, btnFwd)

            // Significance Check: Only send teleport if moved enough
            val currentLoc = textDisplayService.entityOriginalPositions[entity.entityId]
            if (currentLoc == null || currentLoc.distanceSquared(buttonLoc) > 0.000001) {
                textDisplayService.updateLocation(entity, buttonLoc)
            }
        }
    }

    private fun renderLinks(session: Session) {
        val basis = session.basis ?: return
        val player = session.player
        val context = com.typewritermc.core.interaction.context { }

        // 1. Discover all links (Manual + Auto from 'parent' fields)
        val allLinks = mutableListOf<SkillTreeLink>()
        allLinks.addAll(session.linkConfigs)
        
        // Auto-links from buttons
        session.buttons.forEach { btn ->
            val parentId = when (val cfg = btn.config) {
                is btcrenaud.advancedmenus.api.TextMenuButton -> cfg.parent
                is btcrenaud.advancedmenus.api.ItemMenuButton -> cfg.parent
                else -> null
            }
            if (parentId != null && allLinks.none { it.fromId == parentId && it.toId == btn.name }) {
                allLinks.add(SkillTreeLink(fromId = parentId, toId = btn.name))
            }
        }
        // Auto-links from decorations
        session.decorationConfigs.forEach { deco ->
            if (deco.parent != null && allLinks.none { it.fromId == deco.parent && it.toId == deco.id }) {
                allLinks.add(SkillTreeLink(fromId = deco.parent, toId = deco.id))
            }
        }

        if (allLinks.isEmpty()) return

        // Manage entity counts
        while (session.linkEntities.size < allLinks.size) {
            session.linkEntities.add(WrapperEntity(com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.ITEM_DISPLAY))
            session.linkArrowEntities.add(WrapperEntity(com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.ITEM_DISPLAY))
        }

        allLinks.forEachIndexed { index, link ->
            val rodEntity = session.linkEntities[index]
            val headEntity = session.linkArrowEntities[index]

            // 1. Criteria check
            if (!link.criteria.matches(player, context)) {
                textDisplayService.setOpacity(rodEntity, 0.0)
                textDisplayService.setOpacity(headEntity, 0.0)
                return@forEachIndexed
            }

            // 2. Find source and target coordinates + hitboxes
            val sourceInfo = findElementInfo(session, link.fromId) ?: return@forEachIndexed
            val targetInfo = findElementInfo(session, link.toId) ?: return@forEachIndexed

            // 3. Determine Style
            val state = link.states.filter { it.criteria.matches(player, context) }
                                   .maxByOrNull { it.priority }
            val style = state?.style ?: link.style
            
            // 4. Calculate Ray-Box Intersection Points for "Edge-to-Edge" snapping
            val points = calculateLinkPoints(sourceInfo, targetInfo, style.margin)
            val p1 = points.first // Start point on source edge
            val p2 = points.second // End point on target edge

            // 5. Calculate World Positions
            val fwd = session.baseForwardDistance * (1.0 / session.zoomLevel)
            val btnFwd = fwd - (style.layer * 0.01)
            
            val loc1 = basis.screenToWorld(session.cameraLocation, p1.first, p1.second, btnFwd)
            val loc2 = basis.screenToWorld(session.cameraLocation, p2.first, p2.second, btnFwd)
            
            // 6. Rod Update
            if (!rodEntity.isSpawned) {
                spawnLinkItem(player, rodEntity, loc1, loc2, style.material, 0, style, session.screenScale)
            } else {
                updateLinkEntityPositions(rodEntity, loc1, loc2, style, session.screenScale)
            }

            // 7. Arrowhead Update
            val showArrow = style.arrowhead != null && link.arrowCriteria.matches(player, context)
            if (showArrow) {
                val arrowCfg = style.arrowhead
                val arrowFwd = btnFwd - (arrowCfg.layerOffset * 0.01)
                val arrowLoc = basis.screenToWorld(session.cameraLocation, p2.first, p2.second, arrowFwd)
                
                if (!headEntity.isSpawned) {
                    spawnLinkItem(player, headEntity, arrowLoc, loc1, arrowCfg.material, arrowCfg.customModelData, style, session.screenScale, arrowCfg.scale)
                } else {
                    updateArrowheadEntity(headEntity, arrowLoc, loc1, style, session.screenScale, arrowCfg.scale)
                }
                textDisplayService.setOpacity(headEntity, 1.0)
            } else {
                textDisplayService.setOpacity(headEntity, 0.0)
            }
        }
    }

    private data class ElementInfo(val x: Double, val y: Double, val w: Double, val h: Double)

    private fun findElementInfo(session: Session, id: String): ElementInfo? {
        session.buttons.find { it.name == id }?.let { btn ->
            val pos = getEffectivePosition(session, btn)
            return ElementInfo(pos.first, pos.second, btn.width * session.zoomLevel, btn.height * session.zoomLevel)
        }
        session.decorationConfigs.find { it.id == id }?.let { deco ->
            // Assume decorations have a baseline size of 5x5 units if not specified
            val dummyButton = ButtonInfo(name = id, screenX = deco.anchorX + deco.offsetX, screenY = deco.anchorY + deco.offsetY, width = 5.0, height = 5.0, config = deco)
            val pos = getEffectivePosition(session, dummyButton)
            return ElementInfo(pos.first, pos.second, 5.0 * session.zoomLevel, 5.0 * session.zoomLevel)
        }
        return null
    }

    private fun calculateLinkPoints(source: ElementInfo, target: ElementInfo, margin: Double): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        val dx = target.x - source.x
        val dy = target.y - source.y
        val angle = Math.atan2(dy, dx)
        
        // Find intersection with source box (center to outside)
        val sX = Math.cos(angle)
        val sY = Math.sin(angle)
        val sourcePoint = intersectRect(source.x, source.y, source.w + margin * 2, source.h + margin * 2, sX, sY)
        
        // Find intersection with target box (center to outside, opposite angle)
        val tX = Math.cos(angle + Math.PI)
        val tY = Math.sin(angle + Math.PI)
        val targetPoint = intersectRect(target.x, target.y, target.w + margin * 2, target.h + margin * 2, tX, tY)
        
        return sourcePoint to targetPoint
    }

    private fun intersectRect(cx: Double, cy: Double, w: Double, h: Double, dx: Double, dy: Double): Pair<Double, Double> {
        val halfW = w / 2.0
        val halfH = h / 2.0
        
        if (Math.abs(dx) < 0.0001) return Pair(cx, cy + (if (dy > 0) halfH else -halfH))
        if (Math.abs(dy) < 0.0001) return Pair(cx + (if (dx > 0) halfW else -halfW), cy)
        
        val t1 = halfW / Math.abs(dx)
        val t2 = halfH / Math.abs(dy)
        val t = Math.min(t1, t2)
        
        return Pair(cx + dx * t, cy + dy * t)
    }

    private fun spawnLinkItem(player: Player, entity: WrapperEntity, start: Location, end: Location, materialName: String, cmd: Int?, style: LinkStyle, screenScale: Double, customScale: Double = 1.0) {
        val material = try { Material.valueOf(materialName.uppercase()) } catch (e: Exception) { Material.WHITE_CONCRETE }
        
        val item = com.typewritermc.engine.paper.utils.item.CustomItem(
            components = listOf(
                com.typewritermc.engine.paper.utils.item.components.ItemMaterialComponent(com.typewritermc.engine.paper.entry.entries.ConstVar(material)),
                com.typewritermc.engine.paper.utils.item.components.ItemCustomModelDataComponent(com.typewritermc.engine.paper.entry.entries.ConstVar(LegacyCustomModelData(cmd ?: 0)))
            )
        ).build(player).toPacketItem()

        entity.meta<ItemDisplayMeta> {
            this.item = item
            billboardConstraints = AbstractDisplayMeta.BillboardConstraints.FIXED
            brightnessOverride = (15 shl 4) or 15
        }
        
        if (customScale == 1.0) {
            updateLinkEntityPositions(entity, start, end, style, screenScale)
        } else {
            updateArrowheadEntity(entity, start, end, style, screenScale, customScale)
        }
        
        entity.spawn(start.toPacketLocation())
        entity.addViewer(player.uniqueId)
    }

    private fun updateArrowheadEntity(entity: WrapperEntity, at: Location, pointingTo: Location, style: LinkStyle, screenScale: Double, headScale: Double) {
        val diff = pointingTo.toVector().subtract(at.toVector())
        val direction = if (diff.length() > 0.001) diff.normalize() else Vector(0, 0, 1)
        
        val yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z)).toFloat()
        val pitch = Math.toDegrees(Math.asin(direction.y)).toFloat()
        
        val scale = (headScale * 0.1 * screenScale).toFloat()
        
        entity.meta<ItemDisplayMeta> {
            this.scale = Vector3f(scale, scale, scale)
        }
        
        val loc = at.clone()
        loc.yaw = yaw
        loc.pitch = pitch
        entity.teleport(loc.toPacketLocation())
    }

    private fun updateLinkEntityPositions(entity: WrapperEntity, start: Location, end: Location, style: LinkStyle, screenScale: Double) {
        val diff = end.toVector().subtract(start.toVector())
        val length = diff.length()
        if (length < 0.001) {
            entity.meta<ItemDisplayMeta> { scale = Vector3f(0f, 0f, 0f) }
            return
        }

        // Midpoint for the rod
        val mid = start.clone().add(diff.clone().multiply(0.5))
        
        // Orientation
        val direction = diff.clone().normalize()
        val yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z)).toFloat()
        val pitch = Math.toDegrees(Math.asin(direction.y)).toFloat()
        
        val thickness = (style.thickness * 0.02 * screenScale).toFloat()
        
        entity.meta<ItemDisplayMeta> {
            scale = Vector3f(thickness, thickness, length.toFloat())
            transformationInterpolationDuration = 0
        }
        
        val loc = mid.clone()
        loc.yaw = yaw
        loc.pitch = pitch
        
        entity.teleport(loc.toPacketLocation())
    }

    fun setHidingUI(player: Player, hiding: Boolean) {
        val session = sessions[player.uniqueId] ?: return
        session.isHidingUI = hiding
        
        // Immediate visual update
        if (hiding) {
            session.buttons.forEach { it.entity?.let { e -> textDisplayService.setOpacity(e, 0.0) } }
            session.layers.forEach { textDisplayService.setOpacity(it, 0.0) }
            session.decorations.forEach { textDisplayService.setOpacity(it, 0.0) }
            session.cursorEntity?.let { textDisplayService.setOpacity(it, 0.0) }
            session.infoAreaEntity?.let { textDisplayService.setOpacity(it, 0.0) }
        } else {
            applyTransforms(session)
            session.cursorEntity?.let { textDisplayService.setOpacity(it, 1.0) }
        }
    }

    private fun updateCursorEntity(player: Player, session: Session) {
        val cursorEnt = session.cursorEntity ?: return
        val basis = session.basis ?: return

        // 0.05 distance offset guarantees the cursor renders slightly in front of 
        // the button models, preventing it from clipping and becoming invisible.
        val cursorPlaneDistance = (session.forwardDistance - 0.05).coerceAtLeast(0.05)
        val screenPosForCursor = CameraBasis.anchorToWorld(
            session.cursorX, session.cursorY, 0.0, 0.0, session.playerFov, cursorPlaneDistance, session.fovCalibration
        )
        val newCursorLoc = basis.screenToWorld(session.cameraLocation, screenPosForCursor.x, screenPosForCursor.y, cursorPlaneDistance)
        textDisplayService.updateLocation(cursorEnt, newCursorLoc)
    }

    private fun performHoverDetection(player: Player, session: Session) {
        // Button screenX/Y and width/height are now stored in anchor-space (0-100)
        // cursorX/cursorY are already in anchor-space (0-100)
        val cx = session.cursorX
        val cy = session.cursorY

        var bestButton: ButtonInfo? = null
        var bestIndex = -1

        session.buttons.forEachIndexed { i, btn ->
            if (btn.entity == null || !btn.enabled) return@forEachIndexed
            val (effX, effY) = getEffectivePosition(session, btn)
            
            // Frustum Culling / Masking check: if opacity would be 0, button is not interactable
            if (isCulled(session, btn, effX, effY)) return@forEachIndexed

            val halfW = btn.width / 2.0
            val halfH = btn.height / 2.0
            
            val inBounds = cx >= effX - halfW && cx <= effX + halfW &&
                           cy >= effY - halfH && cy <= effY + halfH

            if (inBounds) {
                val currentBest = bestButton
                if (currentBest == null || btn.priority >= currentBest.priority) {
                    bestButton = btn
                    bestIndex = i
                }
            }
        }

        session.buttons.forEachIndexed { i, btn ->
            val shouldBeHovered = (i == bestIndex)
            val wasHovered = btn.hovered
            
            if (shouldBeHovered != wasHovered) {
                btn.hovered = shouldBeHovered
                if (btn.isLocked) return@forEachIndexed

                val entity = btn.entity ?: return@forEachIndexed
                val effects = when (val cfg = btn.config) {
                    is TextMenuButton -> cfg.hoverEffects
                    is ItemMenuButton -> cfg.hoverEffects
                    is InputMenuButton -> cfg.hoverEffects
                    else -> emptyList()
                }

                // Always use btn.baseScale — it's set at ButtonInfo creation from cfg.scale.
                // Reading from cfg directly fails for AdvancedMenuButtonConfig (BTCSky type) which
                // falls into `else -> 1.0` and breaks all BTCSky hover effects.
                val baseScale = btn.baseScale

                // Delegate ALL hover logic (including default 1.15x scale) to TextDisplayService
                textDisplayService.applyHoverEffects(entity, effects, baseScale, session.screenScale, shouldBeHovered)

                if (shouldBeHovered) {
                    val sound = btn.hoverSound
                    if (sound != com.typewritermc.engine.paper.utils.Sound.EMPTY) {
                        val context = com.typewritermc.core.interaction.context { }
                        // Ensure sound plays on the player's region thread (needed for Folia)
                        Bukkit.getRegionScheduler().execute(plugin, player.location) {
                            sound.play(player, context)
                        }
                    }
                }

                // Handle linked decoration hover effect if applicable
                val linkedDecoId = when (val cfg = btn.config) {
                    is TextMenuButton -> cfg.linkedDecorationId
                    is ItemMenuButton -> cfg.linkedDecorationId
                    else -> null
                }
                if (linkedDecoId != null) {
                    val decoHoverScale = when (val cfg = btn.config) {
                        is TextMenuButton -> cfg.linkedDecorationHoverScale
                        is ItemMenuButton -> cfg.linkedDecorationHoverScale
                        else -> 1.0
                    }
                    val decoIndex = session.decorationConfigs.indexOfFirst { it.id == linkedDecoId }
                    if (decoIndex in session.decorations.indices && decoIndex in session.decorationConfigs.indices) {
                        val decoEntity = session.decorations[decoIndex]
                        val baseDecoScale = session.decorationConfigs[decoIndex].scale
                        val targetScale = if (shouldBeHovered) baseDecoScale * decoHoverScale else baseDecoScale
                        Bukkit.getRegionScheduler().execute(plugin, player.location) {
                            textDisplayService.setEntityScale(decoEntity, targetScale.toFloat())
                        }
                    }
                }
            }
        }

        // --- Info Area (Lore) Update ---
        val infoContent = bestButton?.infoAreaContent ?: bestButton?.lore
        if (infoContent != null && infoContent.isNotEmpty()) {
            textDisplayService.updateInfoArea(player, session.infoAreaEntity, infoContent)
        } else {
            textDisplayService.hideInfoArea(player, session.infoAreaEntity)
        }
    }

    fun startSession(player: Player, session: Session) {
        // Check if this is a sub-menu navigation (player already has an active session)
        val existingSession = sessions[player.uniqueId]
        val isSubMenuNav = existingSession != null
        
        if (isSubMenuNav) {
            // Sub-menu navigation: clean up visual entities only, preserve camera/seat
            cleanupSessionEntities(existingSession)
            
            // Transfer the existing camera infrastructure to the new session
            session.cameraEntity = existingSession.cameraEntity
            session.seatEntity = existingSession.seatEntity
            session.basis = existingSession.basis
            session.spawnYaw = existingSession.spawnYaw
            session.spawnPitch = existingSession.spawnPitch
            session.hasMoved = existingSession.hasMoved
            session.initialX = existingSession.initialX
            session.initialY = existingSession.initialY
            session.lastPlayerYaw = existingSession.lastPlayerYaw
            session.lastPlayerPitch = existingSession.lastPlayerPitch
            // Preserve cursor position for seamless transition
            session.cursorX = existingSession.cursorX
            session.cursorY = existingSession.cursorY
            session.targetCursorX = existingSession.targetCursorX
            session.targetCursorY = existingSession.targetCursorY
        }
        
        sessions[player.uniqueId] = session
        
        // --- Global Config Propagation ---
        val globalConfig = ExtensionConfigManifestEntry.getInstance()
        if (globalConfig != null) {
            // Apply global cursor if session has no item set
            val isEmpty = session.cursorItem == null || session.cursorItem == com.typewritermc.engine.paper.utils.item.Item.Empty
            
            if (isEmpty && globalConfig.defaultCursorMaterial != Material.AIR) {
                session.cursorItem = CustomItem(
                    components = listOf(
                        ItemMaterialComponent(ConstVar(globalConfig.defaultCursorMaterial)),
                        ItemCustomModelDataComponent(ConstVar(LegacyCustomModelData(globalConfig.defaultCursorModel)))
                    )
                )
            }
            
            // Apply global sensitivity/smoothing defaults if they are set to engine defaults
            if (session.cursorSensitivity == 1.0) session.cursorSensitivity = globalConfig.defaultSensitivity
            if (session.cursorSmoothing == 0.3) session.cursorSmoothing = globalConfig.defaultSmoothing
        }
        
        if (!isSubMenuNav) {
            // Trigger global and per-menu open actions
            val config = ExtensionConfigManifestEntry.getInstance()
            config?.globalOpenActions?.triggerEntriesFor(player, context { })
            session.openActions.triggerEntriesFor(player, context { })
            
            // Hide player hand and equipment immediately on new session
            hidePlayerHand(player, session)
        }
        
        if (session.hideChat) {
            // Use engine API to block chat robustly
            player.startBlockingMessages()
            
            if (!isSubMenuNav) {
                // Clear chat for the player on first open.
                // Send 3x a packet of 100 newlines to push all existing messages off screen.
                // The onPacketSend filter allows packets containing "\n\n\n" through.
                val clearPacket = WrapperPlayServerSystemChatMessage(true, Component.text("\n".repeat(100)))
                repeat(3) {
                    PacketEvents.getAPI().playerManager.sendPacket(player, clearPacket)
                }
            }
        }
        
        if (!isSubMenuNav) {
            // First open: full camera setup — dual entity architecture
            session.basis = CameraBasis(session.cameraLocation.yaw, session.cameraLocation.pitch)
            session.spawnYaw = session.cameraLocation.yaw
            session.spawnPitch = session.cameraLocation.pitch

            // === SEAT ENTITY (at player's feet, original position) ===
            // Purpose: mount point for the player. Player's physical hitbox stays at ground level.
            // Mobs can still hit the player. No physical TP required.
            val seatLoc = session.originalLocation.clone()
            val seat = WrapperEntity(com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.ARMOR_STAND)
            seat.meta<me.tofaa.entitylib.meta.other.ArmorStandMeta> {
                isInvisible = true
                isMarker = true
                isSmall = true
            }
            seat.spawn(seatLoc.toPacketLocation())
            seat.addViewer(player.uniqueId)
            session.seatEntity = seat

            // Mount player on seat (locks client-side movement, hitbox stays at ground)
            val seatPassengerPacket = WrapperPlayServerSetPassengers(seat.entityId, intArrayOf(player.entityId))
            PacketEvents.getAPI().playerManager.sendPacket(player, seatPassengerPacket)

            // === CAMERA ENTITY (at eye level, fixed orientation) ===
            // Purpose: provides the locked 3D perspective. Player sees from this entity.
            val cameraDummyLoc = session.cameraLocation.clone()
            val camera = WrapperEntity(com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.ARMOR_STAND)
            camera.meta<me.tofaa.entitylib.meta.other.ArmorStandMeta> {
                isInvisible = true
                isMarker = true
                isSmall = true
            }
            val scanLoc = cameraDummyLoc.toPacketLocation()
            scanLoc.yaw = session.cameraLocation.yaw
            scanLoc.pitch = session.cameraLocation.pitch
            camera.spawn(scanLoc)
            camera.addViewer(player.uniqueId)
            session.cameraEntity = camera

            // Lock player view to camera entity (perspective is fixed, only cursor moves)
            val cameraPacket = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera(camera.entityId)
            PacketEvents.getAPI().playerManager.sendPacket(player, cameraPacket)

            // Movement lock via attribute (resets naturally on reload/disconnect)
            if (session.lockMovement) {
                player.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED)?.baseValue = 0.0
            }
            
            // Safe Mode: only make no-collision if explicitly enabled.
            if (session.safeMode) {
                player.isCollidable = false
            } else {
                player.isCollidable = true
            }

            // Invisibility Logic
            val globalConfig = ExtensionConfigManifestEntry.getInstance()
            val shouldBeInvisible = when (session.invisibilityMode) {
                btcrenaud.advancedmenus.api.InvisibilityMode.FORCE_VISIBLE -> false
                btcrenaud.advancedmenus.api.InvisibilityMode.FORCE_INVISIBLE -> true
                else -> globalConfig?.defaultInvisibility ?: true
            }
            player.isInvisible = shouldBeInvisible

            // Apply Brightness to all entities is handled in tickSessions/applyTransforms after setup.
            // hidePlayerHand calls follow.

            // Hide player hand immediately, then again after 2 ticks.
            hidePlayerHand(player)
            Bukkit.getRegionScheduler().runDelayed(plugin, player.location, { _ ->
                if (sessions.containsKey(player.uniqueId)) {
                    hidePlayerHand(player, sessions[player.uniqueId])
                    // Optimization: Set held slot to 8 (usually empty) to minimize hand model issues
                    player.inventory.heldItemSlot = 8
                }
            }, 2L)
            session.hasMoved = false
        }

        // --- Post-Spawn Cursor Persistence ---
        session.cursorItem?.let { item ->
            if (item != com.typewritermc.engine.paper.utils.item.Item.Empty) {
                val cursorPlaneDistance = (session.forwardDistance - 0.05).coerceAtLeast(0.05)
                val screenPos = CameraBasis.anchorToWorld(
                    session.cursorX, session.cursorY, 0.0, 0.0, session.playerFov, cursorPlaneDistance, session.fovCalibration
                )
                val cursorLoc = session.basis!!.screenToWorld(session.cameraLocation, screenPos.x, screenPos.y, cursorPlaneDistance)
                session.cursorEntity = textDisplayService.spawnCursor(player, item, cursorLoc, session.screenScale, session.cursorScale)
            }
        }
    }

    fun getSession(player: Player): Session? = sessions[player.uniqueId]

    /**
     * Cleans up all ephemeral entities (buttons, layers, decorations, cursor, info area) from a session
     * without restoring the player camera/movement state. Used during sub-menu navigation.
     */
    private fun cleanupSessionEntities(session: Session) {
        val player = session.player
        
        // Despawn cursor
        session.cursorEntity?.let { it.despawn(); it.remove() }
        session.cursorEntity = null
        
        // Despawn info area
        session.infoAreaEntity?.let { it.despawn(); it.remove() }
        
        // Despawn layers
        session.layers.forEach { it.despawn(); it.remove() }
        session.layers.clear()
        
        // Despawn decorations
        session.decorations.forEach { it.despawn(); it.remove() }
        session.decorations.clear()
        
        // Despawn link entities
        session.linkEntities.forEach { it.despawn(); it.remove() }
        session.linkEntities.clear()
        
        // Despawn link arrow entities
        session.linkArrowEntities.forEach { it.despawn(); it.remove() }
        session.linkArrowEntities.clear()
        
        // Despawn button entities
        session.buttons.forEach { btn ->
            btn.entity?.let { it.despawn(); it.remove() }
            btn.entity = null
        }
        session.buttons.clear()
    }

    fun stopSession(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return

        // Trigger per-menu and global close actions
        val config = ExtensionConfigManifestEntry.getInstance()
        session.closeActions.triggerEntriesFor(player, context { })
        config?.globalCloseActions?.triggerEntriesFor(player, context { })

        gracePeriods[player.uniqueId] = System.currentTimeMillis() + 2000L

        // Cleanup all session entities (buttons, layers, decorations, cursor)
        cleanupSessionEntities(session)

        // 1. Unmount player from seat entity
        session.seatEntity?.let {
            val unmountPacket = WrapperPlayServerSetPassengers(it.entityId, intArrayOf())
            PacketEvents.getAPI().playerManager.sendPacket(player, unmountPacket)
            it.removeViewer(player.uniqueId)
            it.despawn()
            it.remove()
        }

        // 2. Release from camera perspective
        val cameraReset = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera(player.entityId)
        PacketEvents.getAPI().playerManager.sendPacket(player, cameraReset)

        // 3. Destroy camera entity
        session.cameraEntity?.let {
            it.removeViewer(player.uniqueId)
            it.despawn()
            it.remove()
        }
        
        // Restore player hand and equipment
        restorePlayerHand(player)

        // Restore movement speed (vanilla default = 0.1)
        player.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
        player.isInvisible = false
        player.isCollidable = true

        if (session.hideChat) {
            player.stopBlockingMessages()
            player.chatHistory.resendMessages(player)
        }

        // Restore player hand visibility
        restorePlayerHand(player)

        // Final nuclear cleanup for all remaining labels
        textDisplayService.removePlayerLabels(player)
    }

    fun startAnimation(session: Session) {
        val player = session.player
        val entities = mutableListOf<WrapperEntity>()
        entities.addAll(session.layers)
        entities.addAll(session.decorations)
        session.buttons.forEach { it.entity?.let { e -> entities.add(e) } }

        val basis = session.basis ?: return
        
        // Slide DOWN from top (Y+2.5) to target position
        val offset = 2.5 
        
        val duration = 15 // 15 ticks for standard smooth entry
        var step = 0
        
        // Capture base positions once to avoid recursive drift (teleporting to already teleported coords)
        val basePositions = entities.associate { it.entityId to textDisplayService.entityOriginalPositions[it.entityId]?.clone() }
        
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { task ->
            if (step > duration) {
                task.cancel()
                return@runAtFixedRate
            }
            
            val t = (step.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
            val currentOffset = offset * (1.0 - t)
            
            entities.forEach { entity ->
                // Use the ORIGINAL target position from the snapshot
                val finalLoc = basePositions[entity.entityId] ?: return@forEach
                val upOffset = basis.up.clone().multiply(currentOffset)
                val animatedLoc = finalLoc.clone().add(upOffset)
                
                textDisplayService.updateLocation(entity, animatedLoc)
            }
            
            step++
        }, 1L, 1L)
    }

    private fun registerPacketListeners() {
        if (packetListener != null) return
        
        packetListener = object : PacketListenerAbstract() {
            override fun onPacketReceive(event: PacketReceiveEvent) {
                try {
                    val type = event.packetType
                    if (type != PacketType.Play.Client.PLAYER_INPUT &&
                        type != PacketType.Play.Client.PLAYER_ROTATION &&
                        type != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION &&
                        type != PacketType.Play.Client.INTERACT_ENTITY &&
                        type != PacketType.Play.Client.ANIMATION &&
                        type != PacketType.Play.Client.USE_ITEM &&
                        type != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT &&
                        type != PacketType.Play.Client.PLAYER_DIGGING &&
                        type != PacketType.Play.Client.HELD_ITEM_CHANGE) {
                        return
                    }

                    val user = event.user ?: return
                    val uuid = user.uuid
                    
                    // Critical: Check if player is still in grace period after menu close
                    val now = System.currentTimeMillis()
                    val graceEnd = gracePeriods[uuid] ?: 0L
                    if (graceEnd > now) {
                        if (type == PacketType.Play.Client.INTERACT_ENTITY || type == PacketType.Play.Client.ANIMATION) {
                            event.isCancelled = true
                        }
                        return
                    }

                    val session = sessions[uuid] ?: return
                    val player = Bukkit.getPlayer(uuid) ?: return

                    when (type) {
                        PacketType.Play.Client.PLAYER_INPUT -> {
                            val pkt = WrapperPlayClientPlayerInput(event)
                            val isMoving = pkt.isForward || pkt.isBackward || pkt.isLeft || pkt.isRight
                            if (isMoving && session.closeOnMove && now - session.creationTime > 800L) {
                                Bukkit.getRegionScheduler().run(plugin, player.location) {
                                    stopSession(player)
                                }
                            }
                        }
                        PacketType.Play.Client.PLAYER_ROTATION,
                        PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION -> {
                            val yaw: Float
                            val pitch: Float
                            if (type == PacketType.Play.Client.PLAYER_ROTATION) {
                                val pkt = WrapperPlayClientPlayerRotation(event)
                                yaw = pkt.yaw
                                pitch = pkt.pitch
                            } else {
                                val pkt = WrapperPlayClientPlayerPositionAndRotation(event)
                                yaw = pkt.yaw
                                pitch = pkt.pitch
                            }
                            computeFromAngles(session, yaw, pitch)
                            subTickCursorUpdate(session)
                        }
                        PacketType.Play.Client.INTERACT_ENTITY -> {
                            event.isCancelled = true
                            val pkt = WrapperPlayClientInteractEntity(event)
                            val action = pkt.action
                            val isRight = action == WrapperPlayClientInteractEntity.InteractAction.INTERACT
                            val isLeft = action == WrapperPlayClientInteractEntity.InteractAction.ATTACK
                            
                            if (isRight || isLeft) {
                                Bukkit.getRegionScheduler().execute(plugin, player.location) {
                                    handleInteraction(player, session, isRightClick = isRight)
                                }
                            }
                        }
                        PacketType.Play.Client.PLAYER_DIGGING -> {
                            event.isCancelled = true // Always cancel digging while in menu
                            val pkt = WrapperPlayClientPlayerDigging(event)
                            if (pkt.action == DiggingAction.START_DIGGING || pkt.action == DiggingAction.FINISHED_DIGGING) {
                                Bukkit.getRegionScheduler().execute(plugin, player.location) {
                                    handleInteraction(player, session, isRightClick = false)
                                }
                            }
                        }
                        PacketType.Play.Client.ANIMATION -> {
                            event.isCancelled = true // Prevents arm swing click-through
                            
                            // Re-calculate cursor position immediately to ensure hitbox accuracy for air-clicks
                            if (session.lastPlayerYaw != null && session.lastPlayerPitch != null) {
                                computeFromAngles(session, session.lastPlayerYaw!!, session.lastPlayerPitch!!)
                            }
                            
                            Bukkit.getRegionScheduler().execute(plugin, player.location) {
                                handleInteraction(player, session, isRightClick = false)
                            }
                        }
                        PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT -> {
                            // Left click against a block surface — treat as primary action (same as ANIMATION)
                            event.isCancelled = true
                            if (session.lastPlayerYaw != null && session.lastPlayerPitch != null) {
                                computeFromAngles(session, session.lastPlayerYaw!!, session.lastPlayerPitch!!)
                            }
                            Bukkit.getRegionScheduler().execute(plugin, player.location) {
                                handleInteraction(player, session, isRightClick = false)
                            }
                        }
                        PacketType.Play.Client.USE_ITEM -> {
                            // Right click in air — secondary action
                            event.isCancelled = true
                            Bukkit.getRegionScheduler().execute(plugin, player.location) {
                                handleInteraction(player, session, isRightClick = true)
                            }
                        }
                        PacketType.Play.Client.HELD_ITEM_CHANGE -> {
                            event.isCancelled = true
                            hidePlayerHand(player)
                            
                            val pkt = com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange(event)
                            val currentSlot = pkt.slot
                            
                            if (session.lastHeldSlot == -1) {
                                session.lastHeldSlot = currentSlot
                            } else {
                                val diff = currentSlot - session.lastHeldSlot
                                val direction = when {
                                    diff == 1 || diff == -8 -> 1   // down / right / zoom out
                                    diff == -1 || diff == 8 -> -1  // up / left / zoom in
                                    else -> 0
                                }
                                session.lastHeldSlot = currentSlot
                                
                                if (direction != 0) {
                                    val step = session.wheelConfig.stepMultiplier * direction
                                    
                                    // Hotbar Gestures (9->1 is direction 1 on some setups, 1->9 is -1)
                                    val context = com.typewritermc.core.interaction.context { }
                                    if (direction > 0) session.wheelConfig.hotbar.onStepForward.triggerEntriesFor(player, context)
                                    else session.wheelConfig.hotbar.onStepBackward.triggerEntriesFor(player, context)
                                    session.wheelConfig.hotbar.onCycle.triggerEntriesFor(player, context)

                                    // 1. Check for specific zones under cursor
                                    val cursorX = session.cursorX
                                    val cursorY = session.cursorY
                                    
                                    val scrollZone = session.wheelConfig.scrollZones.find { 
                                        cursorX >= it.anchorX && cursorX <= it.anchorX + it.width &&
                                        cursorY >= it.anchorY && cursorY <= it.anchorY + it.height
                                    }
                                    
                                    val zoomZone = session.wheelConfig.zoomZones.find { 
                                        val dx = cursorX - it.centerX
                                        val dy = cursorY - it.centerY
                                        it.isAbsolute || (dx*dx + dy*dy <= it.radius * it.radius)
                                    }

                                    when (session.wheelConfig.type) {
                                        btcrenaud.advancedmenus.api.WheelActionType.SCROLL_VERTICAL -> {
                                            // Zone-Based Redirection: 
                                            // If cursor is on the sidebar (0-35), always scroll the global sidebar.
                                            // If cursor is on the content (35-100), scroll the detected zone.
                                            if (cursorX < 35.0) {
                                                session.targetScrollY -= (session.wheelConfig.stepMultiplier * direction)
                                                session.targetScrollY = session.targetScrollY.coerceIn(session.wheelConfig.minBoundary, session.wheelConfig.maxBoundary)
                                            } else if (scrollZone != null) {
                                                val current = session.scrollPagesY[scrollZone.id] ?: 0
                                                val next = (current + direction).coerceIn(0, scrollZone.pagesY - 1)
                                                session.scrollPagesY[scrollZone.id] = next
                                            } else {
                                                // Default content scroll if no zone
                                                session.targetScrollY -= (session.wheelConfig.stepMultiplier * direction)
                                                session.targetScrollY = session.targetScrollY.coerceIn(session.wheelConfig.minBoundary, session.wheelConfig.maxBoundary)
                                            }
                                        }
                                        btcrenaud.advancedmenus.api.WheelActionType.SCROLL_HORIZONTAL -> {
                                            if (scrollZone != null) {
                                                val current = session.scrollPagesX[scrollZone.id] ?: 0
                                                val next = (current + direction).coerceIn(0, scrollZone.pagesX - 1)
                                                session.scrollPagesX[scrollZone.id] = next
                                            } else {
                                                session.targetScrollX += (session.wheelConfig.stepMultiplier * direction)
                                                session.targetScrollX = session.targetScrollX.coerceIn(session.wheelConfig.minBoundary, session.wheelConfig.maxBoundary)
                                            }
                                        }
                                        btcrenaud.advancedmenus.api.WheelActionType.ZOOM -> {
                                            if (zoomZone != null) {
                                                val current = session.targetZoomFactors[zoomZone.id] ?: 1.0
                                                val next = (current - (direction * 0.1)).coerceIn(1.0, zoomZone.maxScale)
                                                session.targetZoomFactors[zoomZone.id] = next
                                            } else {
                                                session.targetZoomLevel -= (direction * 0.05)
                                                session.targetZoomLevel = session.targetZoomLevel.coerceIn(session.wheelConfig.minBoundary, session.wheelConfig.maxBoundary)
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    // Log error but don't crash the Netty thread
                    // plugin.logger.warning("Error in MenuSessionService packet listener: ${e.message}")
                }
            }

            override fun onPacketSend(event: com.github.retrooper.packetevents.event.PacketSendEvent) {
                val player = event.getPlayer<Player>() ?: return
                val session = sessions[player.uniqueId] ?: return
                val type = event.packetType

                // --- Block equipment updates to prevent the hand from reappearing ---
                // When hidePlayerHand sends a blank ENTITY_EQUIPMENT packet, we must NOT
                // cancel it (that would block our own packet). We only cancel server-originated packets.
                // Solution: use a per-session atomic flag 'isHidingEquipment' to identify our own packets.
                if (type == PacketType.Play.Server.ENTITY_EQUIPMENT) {
                    try {
                        val pkt = WrapperPlayServerEntityEquipment(event)
                        if (pkt.entityId == player.entityId && sessions.containsKey(player.uniqueId)) {
                            // If ANY slot contains a non-AIR item, this is a server-side equipment resync
                            // (e.g. triggered by mounting the seat entity). Cancel it and resend blank.
                            val hasNonAir = pkt.equipment.any { equip ->
                                equip.item.type != com.github.retrooper.packetevents.protocol.item.type.ItemTypes.AIR
                            }
                            if (hasNonAir) {
                                event.isCancelled = true
                                hidePlayerHand(player)
                            }
                            // Our own blank packets (all AIR) pass through naturally
                        }
                    } catch (_: Exception) {}
                    return
                }

                // --- Block SET_SLOT and WINDOW_ITEMS for hotbar protection ---
                if (type == PacketType.Play.Server.SET_SLOT) {
                    try {
                        val pkt = com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot(event)
                        // Window 0 is player inventory. Hotbar slots are 36-44 in Window 0 for some versions, or 0-8 for others.
                        // For SET_SLOT (Window 0/Inventory), hotbar is usually 36-44.
                        if (pkt.windowId == 0 && pkt.slot in 36..44) {
                            event.isCancelled = true
                        }
                    } catch (_: Exception) {}
                }

                if (session.hideChat) {
                    // Block all forms of chat packets to ensure total invisibility of the chat HUD
                    if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE ||
                        type == PacketType.Play.Server.CHAT_MESSAGE ||
                        type == PacketType.Play.Server.DISGUISED_CHAT) {

                        // Allow the "clear" packet through (identified by 3+ consecutive newlines)
                        if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
                            val pkt = WrapperPlayServerSystemChatMessage(event)
                            if (PlainTextComponentSerializer.plainText().serialize(pkt.message).contains("\n\n\n")) {
                                return
                            }
                        }

                        event.isCancelled = true
                    }
                }
            }
        }
        PacketEvents.getAPI().eventManager.registerListener(packetListener!!)
    }

    private fun computeFromAngles(session: Session, yaw: Float, pitch: Float) {
        val basis = session.basis ?: return

        // Grace period memory constraints checking
        if (session.grabPanorama) {
            if (session.lastPanoramaX != null && session.lastPanoramaY != null) {
                val dx = (yaw - session.lastPanoramaX!!)
                val dy = (pitch - session.lastPanoramaY!!)
                
                session.panoramaYaw += dx.toFloat()
                session.panoramaPitch += dy.toFloat()
                
                if (session.loop) {
                    session.panoramaYaw %= 360f
                }
                
                session.panoramaPitch = session.panoramaPitch.coerceIn(-90f, 90f)
            }
            session.lastPanoramaX = yaw.toDouble()
            session.lastPanoramaY = pitch.toDouble()
            
            // In grab mode, the effective cursor stays centered (50, 50) 
            // unless we want it to move over the rotated view. 
            // For now, we update the camera rotation metadata if needed.
        }

        if (!session.hasMoved) {
            if (session.initialX == null) {
                session.initialX = yaw.toDouble()
                session.initialY = pitch.toDouble()
            } else {
                val dx = yaw - session.initialX!!
                val dy = pitch - session.initialY!!
                if (dx * dx + dy * dy > 25.0) {
                    session.hasMoved = true
                }
            }
        }

        session.lastPlayerYaw = yaw
        session.lastPlayerPitch = pitch

        val effYaw = if (session.grabPanorama) session.panoramaYaw + session.spawnYaw else yaw
        val effPitch = if (session.grabPanorama) session.panoramaPitch + session.spawnPitch else pitch
        val screenPos = basis.getCursorScreen(effYaw, effPitch, session.spawnYaw, session.spawnPitch, session.forwardDistance)
        val anchorPos = CameraBasis.cursorToAnchor(screenPos.x, screenPos.y, session.playerFov, session.forwardDistance, session.fovCalibration)
        val anchorX = 50.0 + (anchorPos.x - 50.0) * session.cursorSensitivity
        val anchorY = 50.0 + (anchorPos.y - 50.0) * session.cursorSensitivity

        session.targetCursorX = anchorX.coerceIn(session.cursorXMin, session.cursorXMax)
        session.targetCursorY = anchorY.coerceIn(session.cursorYMin, session.cursorYMax)
    }

    private fun subTickCursorUpdate(session: Session) {
        // Task 6: Fluid Cursor - If smoothing is set to instant (>= 1.0), 
        // snap directly to the target per packet to eliminate lag.
        if (session.cursorSmoothing >= 1.0) {
            session.cursorX = session.targetCursorX
            session.cursorY = session.targetCursorY
        } else {
            // For smooth mode, apply a high-frequency easing (packet-rate)
            // But we must use a slightly lower factor than the 20Hz tick to feel smooth.
            val t = session.cursorSmoothing.coerceIn(0.01, 1.0)
            session.cursorX = lerp(session.cursorX, session.targetCursorX, t)
            session.cursorY = lerp(session.cursorY, session.targetCursorY, t)
        }
        
        updateCursorEntity(session.player, session)
        
        // Immediate interaction update if cursor moved significantly
        performHoverDetection(session.player, session)
    }

    private fun syncLinkedDecorations(session: Session) {
        val player = session.player
        session.buttons.forEach { btn ->
            val linkedDecoId = when (val cfg = btn.config) {
                is TextMenuButton -> cfg.linkedDecorationId
                is ItemMenuButton -> cfg.linkedDecorationId
                else -> null
            } ?: return@forEach

            val decoIndex = session.decorationConfigs.indexOfFirst { it.id == linkedDecoId }
            if (decoIndex in session.decorations.indices && decoIndex in session.decorationConfigs.indices) {
                val decoEntity = session.decorations[decoIndex]
                val (effX, effY) = getEffectivePosition(session, btn)
                
                // Recalculate world position for the decoration to match the button's effective UI position
                val basis = session.basis ?: return@forEach
                val planeDistance = session.forwardDistance
                
                val screenPos = CameraBasis.anchorToWorld(
                    effX, effY, 0.0, 0.0, session.playerFov, planeDistance, session.fovCalibration
                )
                val worldLoc = basis.screenToWorld(session.cameraLocation, screenPos.x, screenPos.y, planeDistance)
                
                // Only update if moved to save bandwidth
                textDisplayService.updateLocation(decoEntity, worldLoc)
            }
        }
    }

    fun adjustZoom(player: Player, delta: Double) {
        val session = sessions[player.uniqueId] ?: return
        // Cap expansion zoom at 3.0x as per task 3
        val effectiveMax = minOf(session.wheelConfig.maxBoundary, 3.0)
        session.targetZoomLevel = (session.targetZoomLevel + delta).coerceIn(session.wheelConfig.minBoundary, effectiveMax)
    }

    fun adjustScroll(player: Player, deltaX: Double, deltaY: Double) {
        val session = sessions[player.uniqueId] ?: return
        session.targetScrollX = (session.targetScrollX + deltaX).coerceIn(session.wheelConfig.minBoundary, session.wheelConfig.maxBoundary)
        session.targetScrollY = (session.targetScrollY + deltaY).coerceIn(session.wheelConfig.minBoundary, session.wheelConfig.maxBoundary)
    }

    private fun handleInteraction(player: Player, session: Session, isRightClick: Boolean) {
        val now = System.currentTimeMillis()
        if (now - session.creationTime < 200L) return
        if (now - session.lastClickTime < 150L) return // Reduced from 250ms for better responsiveness
        
        // --- DIAGNOSTIC LOG START ---
        if (session.debug || true) { // Always log for now to troubleshoot non-firing buttons
            val cx = session.cursorX
            val cy = session.cursorY
            
            // Log ALL buttons to see what might be blocking or culled
            session.buttons.asReversed().forEach { btn ->
                val (effX, effY) = getEffectivePosition(session, btn)
                val culled = isCulled(session, btn, effX, effY)
                
                // Safety: Minimum hitbox of 5.0x5.0 if width/height are effectively zero
                val safeW = btn.width.coerceAtLeast(5.0)
                val safeH = btn.height.coerceAtLeast(5.0)
                
                val halfW = safeW / 2.0
                val halfH = safeH / 2.0
                val inBounds = cx >= effX - halfW && cx <= effX + halfW && cy >= effY - halfH && cy <= effY + halfH
                
                if (inBounds || btn.hovered) {
                    val status = buildString {
                        if (inBounds) append("[IN_HITBOX] ")
                        if (culled) append("[CULLED] ")
                        if (!btn.enabled) append("[DISABLED] ")
                        if (btn.isLocked) append("[LOCKED] ")
                        if (btn.hovered) append("[HOVERED] ")
                    }
                    plugin.logger.info("[InteractionTrace] Player ${player.name} | Button '${btn.name}' $status Priority: ${btn.priority} Pos: ($effX, $effY) Cursor: ($cx, $cy)")
                }
            }
        }
        // --- DIAGNOSTIC LOG END ---

        // Find button by hovered flag or fallback hit test
        val hoveredButton = session.buttons.asReversed().find { 
            it.hovered && it.enabled && !isCulled(session, it, getEffectivePosition(session, it).first, getEffectivePosition(session, it).second) 
        } ?: session.buttons.asReversed().find { btn ->
            if (!btn.enabled) return@find false
            val (effX, effY) = getEffectivePosition(session, btn)
            if (isCulled(session, btn, effX, effY)) return@find false
            
            // Apply hitbox safety margin (minimum 5x5)
            val safeW = btn.width.coerceAtLeast(5.0)
            val safeH = btn.height.coerceAtLeast(5.0)
            val halfW = safeW / 2.0
            val halfH = safeH / 2.0
            
            session.cursorX >= effX - halfW && session.cursorX <= effX + halfW &&
            session.cursorY >= effY - halfH && session.cursorY <= effY + halfH
        }
            
        if (hoveredButton == null) {
            plugin.logger.info("[InteractionTrace] No button found at cursor position.")
            return
        }
        
        val hasCallback = if (isRightClick) hoveredButton.onRightClickCallback != null else hoveredButton.onClickCallback != null
        val actionCount = if (isRightClick) hoveredButton.rightClickActions.size else hoveredButton.actions.size
        plugin.logger.info("[InteractionTrace] Hovered: '${hoveredButton.name}' | RightClick: $isRightClick | HasCallback: $hasCallback | Actions: $actionCount | Priority: ${hoveredButton.priority}")
            
        session.lastClickTime = now

        // Logic check: Hit distance validation for interaction hitboxes
        val config = hoveredButton.config
        
        if (hoveredButton.isLocked) return

        val sound = hoveredButton.clickSound
        if (sound != com.typewritermc.engine.paper.utils.Sound.EMPTY) {
            sound.play(player, com.typewritermc.core.interaction.context { })
        }

        if (hoveredButton.isInput) {
            openPaperDialogInput(player, hoveredButton)
            return // Input blocks further actions until submitted
        }

        val stopSettings = hoveredButton.stopMenu
        val actionsToTrigger = if (isRightClick) {
            // ONLY trigger right-click actions if it was a right-click.
            // DO NOT fallback to standard actions.
            hoveredButton.rightClickActions
        } else {
            hoveredButton.actions
        }

        val context = com.typewritermc.core.interaction.context { }

        // Interaction order: Sound -> Close (if enabled) -> Actions -> Callback
        if (stopSettings.enabled) {
            stopSession(player)
        }

        if (actionsToTrigger.isNotEmpty()) {
            actionsToTrigger.triggerEntriesFor(player, context)
        }
        
        // Programmatic callbacks for direct navigation (BTCSky menu routing etc.)
        if (isRightClick) {
            hoveredButton.onRightClickCallback?.let { callback ->
                callback(player)
            }
        } else {
            hoveredButton.onClickCallback?.let { callback ->
                callback(player)
            }
        }
    }

    /**
     * Opens a native Paper dialogue for button input. Supports multiple inputs.
     */
    fun openPaperDialogInput(
        player: Player, 
        button: ButtonInfo, 
        customTitle: String? = null,
        customInputs: List<DialogInput>? = null,
        onComplete: ((Map<String, String>) -> Unit)? = null
    ) {
        val title = (customTitle ?: (button.config as? TextMenuButton)?.content ?: button.name).let {
             MiniMessage.miniMessage().deserialize(it.parsePlaceholders(player))
        }

        val inputs = customInputs ?: listOf(
            DialogInput.text(
                "input",
                200,
                Component.text("Value"),
                true,
                button.inputPlaceholder,
                100,
                null
            )
        )

        val submitAction = ActionButton.builder(Component.text("Confirmer"))
            .action(DialogAction.customClick({ result, _ ->
                val resultMap = inputs.associate { it.key() to (result.getText(it.key()) ?: "") }
                
                // If single input, update button's current value
                if (resultMap.size == 1) {
                    button.currentValue = resultMap.values.first()
                }
                
                button.onInputCallback?.invoke(player, resultMap.values.firstOrNull() ?: "")
                onComplete?.invoke(resultMap)
            }, ClickCallback.Options.builder().build()))
            .build()

        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(DialogBase.builder(title)
                    .inputs(inputs)
                    .build())
                .type(DialogType.multiAction(listOf(submitAction), null, 1))
        }

        player.showDialog(dialog)
    }

    private fun getEffectivePosition(session: Session, button: ButtonInfo): Pair<Double, Double> {
        var effectiveX = button.screenX
        var effectiveY = button.screenY
        
        // Apply scroll offsets if button is in a scroll zone
        val scrollZone = session.wheelConfig.scrollZones.find { zone ->
            button.screenX >= zone.anchorX && button.screenX <= zone.anchorX + zone.width &&
            button.screenY >= zone.anchorY && button.screenY <= zone.anchorY + zone.height
        }
        
        if (scrollZone != null) {
            val pageX = session.targetScrollX / 100.0
            val pageY = session.targetScrollY / 100.0
            
            // Sub-zone mapping: 
            // If virtualWidth/Height are defined, we treat the button's coordinates as relative to the zone's internal 0-100 grid.
            if (scrollZone.virtualWidth != null || scrollZone.virtualHeight != null) {
                val internalX = (button.screenX - scrollZone.anchorX) / scrollZone.width * 100.0
                val internalY = (button.screenY - scrollZone.anchorY) / scrollZone.height * 100.0
                
                // Then apply page offset
                effectiveX = scrollZone.anchorX + ((internalX - session.targetScrollX) / 100.0 * scrollZone.width)
                effectiveY = scrollZone.anchorY + ((internalY - session.targetScrollY) / 100.0 * scrollZone.height)
            } else {
                // Legacy behavior: anchor-based offsetting
                effectiveX -= (pageX * scrollZone.width)
                effectiveY -= (pageY * scrollZone.height)
            }
        } else {
            effectiveX += session.scrollX
            effectiveY += session.scrollY
        }
        
        // --- ADDED: Zoom Scaling for Hitboxes ---
        // Scale the coordinate relative to the zoom pivot
        val zoomScale = session.zoomLevel
        if (zoomScale != 1.0) {
            val pivotX = session.zoomPivotX
            val pivotY = session.zoomPivotY
            
            effectiveX = pivotX + (effectiveX - pivotX) * zoomScale
            effectiveY = pivotY + (effectiveY - pivotY) * zoomScale
        }
        
        return Pair(effectiveX, effectiveY)
    }

    private fun isCulled(session: Session, button: ButtonInfo, effX: Double, effY: Double): Boolean {
        // Frustum Culling (Standard screen bounds)
        if (effX < -50.0 || effX > 150.0 || effY < -50.0 || effY > 150.0) return true
        
        // Scroll Zone Masking logic
        val scrollZone = session.wheelConfig.scrollZones.find { zone ->
            button.screenX >= zone.anchorX && button.screenX <= zone.anchorX + zone.width &&
            button.screenY >= zone.anchorY && button.screenY <= zone.anchorY + zone.height
        }
        
        if (scrollZone != null) {
            // Strict Boundary clipping: hide if button center is outside the zone
            // We use a small epsilon (0.1) for floating point artifacts
            if (effX < scrollZone.anchorX - 0.1 || effX > scrollZone.anchorX + scrollZone.width + 0.1 ||
                effY < scrollZone.anchorY - 0.1 || effY > scrollZone.anchorY + scrollZone.height + 0.1) {
                return true
            }
            
            // If clip is enabled, even partial overlaps outside are treated as culled if center is out
            if (scrollZone.clip) {
                 val halfW = button.width / 2.0
                 val halfH = button.height / 2.0
                 // If ANY part of the hitbox is outside, we could return true here, but usually center-check is enough.
                 // Let's stick with center check for smooth list scrolling.
            }
        }
        
        return false
    }

    private fun renderDebugHitboxes(session: Session) {
        val player = session.player
        val particles = org.bukkit.Color.fromRGB(255, 0, 0)
        val dust = org.bukkit.Particle.DustOptions(particles, 1.0f)
        
        session.buttons.forEach { btn ->
            if (!btn.enabled) return@forEach
            val (effX, effY) = getEffectivePosition(session, btn)
            if (isCulled(session, btn, effX, effY)) return@forEach
            
            val halfW = btn.width / 2.0
            val halfH = btn.height / 2.0
            
            // Corners in anchor space
            val corners = listOf(
                Pair(effX - halfW, effY - halfH),
                Pair(effX + halfW, effY - halfH),
                Pair(effX + halfW, effY + halfH),
                Pair(effX - halfW, effY + halfH)
            )
            
            corners.forEach { (cx, cy) ->
                val fwd = session.baseForwardDistance * (1.0 / session.zoomLevel)
                val worldPos = CameraBasis.anchorToWorld(cx, cy, 0.0, 0.0, session.playerFov, fwd)
                val btnFwd = fwd - (btn.priority * 0.01)
                val loc = session.basis!!.screenToWorld(session.cameraLocation, worldPos.x, worldPos.y, btnFwd)
                
                player.spawnParticle(org.bukkit.Particle.DUST, loc, 1, dust)
            }
        }
    }

    /** Send blank equipment to hide the player's hand.
     * Sends AIR to all equipment slots — safe to call from any thread.
     */
    private fun hidePlayerHand(player: Player, session: Session? = null) {
        try {
            val air = com.github.retrooper.packetevents.protocol.item.ItemStack.builder()
                .type(com.github.retrooper.packetevents.protocol.item.type.ItemTypes.AIR)
                .amount(1)
                .build()

            val equipment = listOf(
                Equipment(EquipmentSlot.MAIN_HAND, air),
                Equipment(EquipmentSlot.OFF_HAND, air),
                Equipment(EquipmentSlot.HELMET, air),
                Equipment(EquipmentSlot.CHEST_PLATE, air),
                Equipment(EquipmentSlot.LEGGINGS, air),
                Equipment(EquipmentSlot.BOOTS, air)
            )

            val packet = WrapperPlayServerEntityEquipment(player.entityId, equipment)
            PacketEvents.getAPI().playerManager.sendPacket(player, packet)
        } catch (_: Exception) {}
    }

    private fun restorePlayerHand(player: Player) {
        val inv = player.inventory
        val main = inv.itemInMainHand.toPacketItem()
        val off = inv.itemInOffHand.toPacketItem()
        val helmet = inv.helmet?.toPacketItem()
        val chest = inv.chestplate?.toPacketItem()
        val legs = inv.leggings?.toPacketItem()
        val boots = inv.boots?.toPacketItem()
        
        val equipment = listOf(
            Equipment(EquipmentSlot.MAIN_HAND, main),
            Equipment(EquipmentSlot.OFF_HAND, off),
            Equipment(EquipmentSlot.HELMET, helmet),
            Equipment(EquipmentSlot.CHEST_PLATE, chest),
            Equipment(EquipmentSlot.LEGGINGS, legs),
            Equipment(EquipmentSlot.BOOTS, boots)
        )
        
        val packet = WrapperPlayServerEntityEquipment(player.entityId, equipment)
        PacketEvents.getAPI().playerManager.sendPacket(player, packet)
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (sessions.containsKey(event.player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockDamage(event: org.bukkit.event.block.BlockDamageEvent) {
        if (sessions.containsKey(event.player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (sessions.containsKey(event.player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        if (sessions.containsKey(event.player.uniqueId)) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
            event.isCancelled = true
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        if (damager is Player && sessions.containsKey(damager.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (sessions.containsKey(event.player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        if (sessions.containsKey(event.player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        
        stopSession(player)
        
        // Force-reset visibility and collidability in case stopSession missed anything
        player.isInvisible = false
        player.isCollidable = true
        player.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED)?.baseValue = 0.1
    }

    @EventHandler
    fun onSneak(event: org.bukkit.event.player.PlayerToggleSneakEvent) {
        if (!event.isSneaking) return
        val session = sessions[event.player.uniqueId] ?: return
        Bukkit.getRegionScheduler().run(plugin, event.player.location) {
            stopSession(event.player)
        }
    }
}
