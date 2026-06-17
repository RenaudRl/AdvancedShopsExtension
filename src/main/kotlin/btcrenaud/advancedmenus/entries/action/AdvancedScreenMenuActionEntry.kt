package btcrenaud.advancedmenus.entries.action

import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.ActionEntry
import com.typewritermc.engine.paper.entry.entries.ActionTrigger
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.core.utils.point.Position
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.utils.Color
import btcrenaud.advancedmenus.api.*
import btcrenaud.advancedmenus.services.*
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import com.typewritermc.core.interaction.context
import btcrenaud.advancedmenus.util.CameraBasis
import btcrenaud.advancedmenus.entries.artifact.FovCalibrationEntry
import btcrenaud.advancedmenus.util.Vec2
import com.typewritermc.engine.paper.utils.Sound
import com.typewritermc.engine.paper.utils.toBukkitLocation
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import org.bukkit.Location
import org.bukkit.entity.Player
import me.tofaa.entitylib.wrapper.WrapperEntity
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import com.typewritermc.engine.paper.TypewriterPaperPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.UUID

/**
 * Action entry that opens an advanced 3D screen menu for the player.
 *
 * All positioning uses anchor-space (0-100%) for resolution-independent layout.
 */
@Entry("advanced_screen_menu", "Advanced Screen Menu", Colors.PURPLE, "mdi:monitor-screenshot")
class AdvancedScreenMenuActionEntry(
    @Help("Camera location and rotation") val camera: Var<Position> = ConstVar(Position.ORIGIN),
    @Help("Background Sound") val sound: Sound = Sound.EMPTY,
    @Help("Use player current location instead of fixed coordinates") val usePlayerLocation: Boolean = false,
    @Help("Close the menu when player moves (WASD)") val closeOnMove: Boolean = false,

    @Help("Screen plane configuration")
    val screen: MenuScreen = MenuScreen(),

    @Help("Background layers")
    val layers: List<MenuLayer> = emptyList(),

    @Help("Interactive text buttons")
    val textButtons: List<TextMenuButton> = emptyList(),

    @Help("Interactive item buttons")
    val itemButtons: List<ItemMenuButton> = emptyList(),

    @Help("Interactive input buttons")
    val inputButtons: List<InputMenuButton> = emptyList(),

    @Help("Non-interactive decoration elements")
    val decorations: List<DecorationElement> = emptyList(),

    @Help("Skill tree connections between buttons")
    val links: List<SkillTreeLink> = emptyList(),

    @Help("Cursor configuration")
    val cursor: MenuCursor = MenuCursor(),

    @Help("Actions triggered when the menu opens")
    val onOpenActions: List<Ref<ActionEntry>> = emptyList(),

    @Help("FOV Calibration settings artifact")
    val fovSettings: Ref<FovCalibrationEntry> = com.typewritermc.core.entries.emptyRef(),

    @Help("Whether to billboard items/text to face the camera by default")
    val faceCamera: Boolean = false,
    @Help("Safe Mode: If true, the player is invincible/invisible/no-collision and cannot take damage.")
    val safeMode: Boolean = false,
    @Help("Actions triggered when the menu closes")
    val onCloseActions: List<Ref<ActionEntry>> = emptyList(),

    override val id: String = "",
    override val name: String = "",
    override val criteria: List<Criteria> = emptyList(),
    override val modifiers: List<Modifier> = emptyList(),
    override val triggers: List<Ref<TriggerableEntry>> = emptyList()
) : ActionEntry, KoinComponent {

    override fun ActionTrigger.execute() {
        // Fix: Move service injection into execute method to stay stateless
        val sessionService: MenuSessionService = getKoin().get()
        val textDisplayService: TextDisplayService = getKoin().get()
        val screenDetectionService: ScreenDetectionService = getKoin().get()
        val imageDownloadService: ImageDownloadService = getKoin().get()

        val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)
        val originalLoc = player.location.clone()

        Bukkit.getRegionScheduler().execute(plugin, originalLoc) {
            // Fix: Only stop session if no active session exists to prevent "Micro-TP" / jitter during sub-menu navigation.
            // MenuSessionService.startSession handles transitioning existing camera/seat automatically.
            if (sessionService.getSession(player) == null) {
                sessionService.stopSession(player)
            }

            val cameraLoc: Location = if (usePlayerLocation) {
                player.eyeLocation.clone().apply {
                    pitch = 0.0f
                    y += 0.1  // Minimal elevation — camera slightly above ground, hitbox remains at originalLoc
                }
            } else {
                val cameraPos = camera.get(player, context)
                cameraPos.toBukkitLocation().apply {
                    y += 2.62
                    pitch = 0.0f
                }
            }

            // Note: player.teleport() intentionally removed.
            // The dual entity architecture in startSession (seat @ originalLoc + camera @ cameraLoc)
            // eliminates the need for physical teleportation. The player's hitbox stays at ground level.

            if (sound != Sound.EMPTY) {
                sound.play(player, context)
            }

            val basis = CameraBasis(cameraLoc.yaw, cameraLoc.pitch)
            val playerFov = screenDetectionService.getPlayerFov(player)
            val fwd = CameraBasis.adjustDistanceForFov(screen.forwardDistance, actualFov = playerFov)

            // Calculate FOV Scale Multiplier to make UI bigger on high FOVs
            val fovMultiplier = if (screen.fovScalingEnabled) (playerFov / 70.0) else 1.0
            val screenScale = screen.screenScale * fovMultiplier
            
            val calibrationEntry = fovSettings.get()
            val calibration = calibrationEntry?.let { 
                btcrenaud.advancedmenus.util.CameraBasis.Companion.CalibrationParams(it.baseFov, it.baseAspect, it.correctionFactor)
            } ?: btcrenaud.advancedmenus.util.CameraBasis.Companion.CalibrationParams()

            // --- Unified Layering & Spawning ---
            // To prevent Z-fighting and ensure correct layering (faisciations/decorations/buttons),
            // we calculate all depths first, sort them BACK-TO-FRONT, and then spawn.
            
            class RenderTask(
                val depth: Double,
                val spawn: () -> WrapperEntity?
            )
            val renderQueue = mutableListOf<RenderTask>()

            // 1. Layers (Furthest Background: Base +0.02)
            layers.forEach { layer ->
                val layerFwd = fwd + 0.02 - (layer.layer * 0.01)
                renderQueue.add(RenderTask(layerFwd) {
                    val worldPos = CameraBasis.anchorToWorld(layer.anchorX, layer.anchorY, layer.offsetX, layer.offsetY, playerFov, fwd, calibration)
                    val layerLoc = basis.screenToWorld(cameraLoc, worldPos.x, worldPos.y, layerFwd)
                    val finalScale = layer.scale

                    if (layer.imageUrl.isNotEmpty()) {
                        imageDownloadService.downloadAndDisplay(player, layerLoc, layer.imageUrl, finalScale, layer.layer)
                    } 
                    
                    val resolvedContent = layer.content.parsePlaceholders(player)
                    if (resolvedContent.isNotEmpty()) {
                        val estimatedWidthBlocks = resolvedContent.length * 6.0 * 0.025 * finalScale
                        val alignmentOffsetBlocks = textDisplayService.getAlignmentOffsetMultiplier(layer.alignment) * estimatedWidthBlocks
                        val layerLocAdjusted = basis.screenToWorld(cameraLoc, worldPos.x + alignmentOffsetBlocks, worldPos.y, layerFwd)
                        
                        textDisplayService.spawnLabel(player, layerLocAdjusted, TextDisplayService.TextDisplaySettings(
                            content = resolvedContent, scale = finalScale, faceCamera = layer.faceCamera,
                            backgroundColor = layer.backgroundColor, backgroundAlpha = layer.backgroundAlpha,
                            textOpacity = layer.textOpacity, lineWidth = layer.lineWidth,
                            alignment = layer.alignment, shadow = layer.shadow, seeThroughBlocks = layer.seeThroughBlocks
                        ))
                    } else null
                })
            }

            // 2. Decorations (Middle Background: Base +0.01)
            decorations.forEach { deco ->
                val decoFwd = fwd + 0.01 - (deco.layer * 0.01)
                renderQueue.add(RenderTask(decoFwd) {
                    val worldPos = CameraBasis.anchorToWorld(deco.anchorX + deco.offsetX, deco.anchorY + deco.offsetY, 0.0, 0.0, playerFov, fwd, calibration)
                    val decoLoc = basis.screenToWorld(cameraLoc, worldPos.x, worldPos.y, decoFwd)
                    val finalScale = deco.scale

                    if (deco.isItem) {
                        val rItem = deco.item.get(player, context)
                        if (rItem != Item.Empty) {
                            textDisplayService.spawnItemButton(player, decoLoc, rItem, finalScale, 1.0, deco.faceCamera, deco.rotationX.toFloat(), deco.rotationY.toFloat(), deco.rotationZ.toFloat())
                        } else null
                    } else if (deco.content.isNotEmpty()) {
                        val resolvedContent = deco.content.parsePlaceholders(player)
                        val estimatedWidthBlocks = resolvedContent.length * 6.0 * 0.025 * finalScale
                        val alignmentOffsetBlocks = textDisplayService.getAlignmentOffsetMultiplier(deco.alignment) * estimatedWidthBlocks
                        val decoLocAdjusted = basis.screenToWorld(cameraLoc, worldPos.x + alignmentOffsetBlocks, worldPos.y, decoFwd)
                        textDisplayService.spawnLabel(player, decoLocAdjusted, TextDisplayService.TextDisplaySettings(
                            content = resolvedContent, scale = finalScale, faceCamera = deco.faceCamera,
                            backgroundColor = deco.backgroundColor, backgroundAlpha = deco.backgroundAlpha,
                            alignment = deco.alignment, shadow = deco.shadow, seeThroughBlocks = deco.seeThroughBlocks
                        ))
                    } else null
                })
            }

            // 3. Info Area (Near Foreground: Base +0.005)
            var infoAreaEntity: WrapperEntity? = null
            if (screen.infoArea != null) {
                val area = screen.infoArea
                val areaFwd = fwd + 0.005 - (area.layer * 0.01)
                renderQueue.add(RenderTask(areaFwd) {
                    val worldPos = CameraBasis.anchorToWorld(area.anchorX, area.anchorY, 0.0, 0.0, playerFov, fwd, calibration)
                    val areaLoc = basis.screenToWorld(cameraLoc, worldPos.x, worldPos.y, areaFwd)
                    infoAreaEntity = textDisplayService.spawnLabel(player, areaLoc, TextDisplayService.TextDisplaySettings(
                        content = "", scale = area.scale, alignment = area.alignment, lineWidth = area.lineWidth,
                        backgroundColor = area.backgroundColor, backgroundAlpha = area.backgroundAlpha,
                        faceCamera = true, shadow = true
                    ))
                    infoAreaEntity
                })
            }

            // 4. Buttons (Foreground: Base +0.0)
            val sessionButtons = mutableListOf<MenuSessionService.ButtonInfo>()
            
            // Text buttons
            textButtons.forEach { button ->
                if (!button.enabled) return@forEach
                if (!button.criteria.matches(player, context)) return@forEach
                val btnFwd = fwd - (button.priority * 0.01)
                renderQueue.add(RenderTask(btnFwd) {
                    val anchorX = button.anchorX + button.offsetX
                    val anchorY = button.anchorY + button.offsetY
                    val resolvedContent = button.content.parsePlaceholders(player)
                    val finalScale = button.scale
                    val estimatedWidthBlocks = resolvedContent.length * 6.0 * 0.025 * finalScale
                    val estimatedHeightBlocks = 0.25 * finalScale
                    val halfH = fwd * Math.tan(Math.toRadians(playerFov / 2.0)); val halfW = halfH * 1.7777777777777777
                    val buttonWidthAnchor = estimatedWidthBlocks / (2.0 * halfW) * 100.0
                    val buttonHeightAnchor = estimatedHeightBlocks / (2.0 * halfH) * 100.0
                    val alignmentOffsetAnchor = textDisplayService.getAlignmentOffsetMultiplier(button.alignment) * buttonWidthAnchor
                    val shiftedAnchorX = anchorX + alignmentOffsetAnchor
                    val worldPos = CameraBasis.anchorToWorld(shiftedAnchorX, anchorY, 0.0, 0.0, playerFov, fwd, calibration)
                    val buttonLoc = basis.screenToWorld(cameraLoc, worldPos.x, worldPos.y, btnFwd)
                    val metaLineWidth = (estimatedWidthBlocks / (0.025 * finalScale)).toInt().coerceAtLeast(10)

                    val btnEntity = if (resolvedContent.isNotEmpty()) {
                        textDisplayService.spawnLabel(player, buttonLoc, TextDisplayService.TextDisplaySettings(
                            content = resolvedContent, scale = finalScale, faceCamera = button.faceCamera,
                            backgroundColor = button.backgroundColor, backgroundAlpha = button.backgroundAlpha,
                            textOpacity = button.textOpacity, lineWidth = metaLineWidth,
                            shadow = button.shadow, seeThroughBlocks = button.seeThroughBlocks, alignment = button.alignment
                        ))
                    } else null

                    sessionButtons.add(MenuSessionService.ButtonInfo(
                        name = button.name, screenX = shiftedAnchorX, screenY = anchorY, width = buttonWidthAnchor,
                        height = buttonHeightAnchor, entity = btnEntity, config = button, lore = button.lore,
                        baseScale = finalScale, hoverSound = button.hoverSound, clickSound = button.clickSound, stopMenu = button.stopMenu,
                        actions = button.actions, rightClickActions = button.rightClickActions
                    ))
                    btnEntity
                })
            }

            // Input buttons
            inputButtons.forEach { button ->
                if (!button.enabled) return@forEach
                if (!button.criteria.matches(player, context)) return@forEach
                val btnFwd = fwd - (button.priority * 0.01)
                renderQueue.add(RenderTask(btnFwd) {
                    val anchorX = button.anchorX + button.offsetX
                    val anchorY = button.anchorY + button.offsetY
                    val resolvedContent = button.initialValue.parsePlaceholders(player)
                    val finalScale = button.scale
                    val estimatedWidthBlocks = resolvedContent.length.coerceAtLeast(3) * 6.0 * 0.025 * finalScale
                    val estimatedHeightBlocks = 0.25 * finalScale
                    val halfH = fwd * Math.tan(Math.toRadians(playerFov / 2.0)); val halfW = halfH * 1.7777777777777777
                    val buttonWidthAnchor = estimatedWidthBlocks / (2.0 * halfW) * 100.0
                    val buttonHeightAnchor = estimatedHeightBlocks / (2.0 * halfH) * 100.0
                    val alignmentOffsetAnchor = textDisplayService.getAlignmentOffsetMultiplier(button.alignment) * buttonWidthAnchor
                    val shiftedAnchorX = anchorX + alignmentOffsetAnchor
                    val worldPos = CameraBasis.anchorToWorld(shiftedAnchorX, anchorY, 0.0, 0.0, playerFov, fwd, calibration)
                    val buttonLoc = basis.screenToWorld(cameraLoc, worldPos.x, worldPos.y, btnFwd)
                    val metaLineWidth = if (button.lineWidth <= 0) 200 else button.lineWidth.coerceAtLeast(10)

                    val btnEntity = textDisplayService.spawnLabel(player, buttonLoc, TextDisplayService.TextDisplaySettings(
                        content = resolvedContent.ifEmpty { "..." }, scale = finalScale, faceCamera = button.faceCamera,
                        backgroundColor = button.backgroundColor, backgroundAlpha = button.backgroundAlpha,
                        lineWidth = metaLineWidth, alignment = button.alignment, shadow = button.shadow,
                        seeThroughBlocks = button.seeThroughBlocks, textOpacity = button.textOpacity
                    ))

                    sessionButtons.add(MenuSessionService.ButtonInfo(
                        name = button.name.ifEmpty { UUID.randomUUID().toString() }, screenX = shiftedAnchorX, screenY = anchorY,
                        width = buttonWidthAnchor, height = buttonHeightAnchor, entity = btnEntity, config = button,
                        currentValue = resolvedContent, lore = button.lore, baseScale = finalScale, hoverSound = button.hoverSound,
                        clickSound = button.clickSound, stopMenu = StopMenuSettings(enabled = false), isInput = true,
                        inputPlaceholder = button.placeholder
                    ))
                    btnEntity
                })
            }

            // Item buttons
            itemButtons.forEach { button ->
                if (!button.enabled) return@forEach
                if (!button.criteria.matches(player, context)) return@forEach
                val btnFwd = fwd - (button.priority * 0.01) + button.depthOffset
                renderQueue.add(RenderTask(btnFwd) {
                    val anchorX = button.anchorX + button.offsetX
                    val anchorY = button.anchorY + button.offsetY
                    val finalScale = button.scale
                    val estimatedWidthBlocks = 1.0 * finalScale
                    val estimatedHeightBlocks = 1.0 * finalScale
                    val halfH = fwd * Math.tan(Math.toRadians(playerFov / 2.0)); val halfW = halfH * 1.7777777777777777
                    val buttonWidthAnchor = estimatedWidthBlocks / (2.0 * halfW) * 100.0
                    val buttonHeightAnchor = estimatedHeightBlocks / (2.0 * halfH) * 100.0
                    val worldPos = CameraBasis.anchorToWorld(anchorX, anchorY, 0.0, 0.0, playerFov, fwd, calibration)
                    val buttonLoc = basis.screenToWorld(cameraLoc, worldPos.x, worldPos.y, btnFwd)
                    val rItem = button.item.get(player, context)

                    val btnEntity = if (rItem != Item.Empty) {
                        textDisplayService.spawnItemButton(player, buttonLoc, rItem, finalScale, screenScale, button.faceCamera, button.rotationX.toFloat(), button.rotationY.toFloat(), button.rotationZ.toFloat())
                    } else null

                    sessionButtons.add(MenuSessionService.ButtonInfo(
                        name = button.name, screenX = anchorX, screenY = anchorY, width = buttonWidthAnchor,
                        height = buttonHeightAnchor, entity = btnEntity, config = button, lore = button.lore,
                        baseScale = finalScale, hoverSound = button.hoverSound, clickSound = button.clickSound, stopMenu = button.stopMenu,
                        actions = button.actions, rightClickActions = button.rightClickActions
                    ))
                    btnEntity
                })
            }

            // ── Execute Spawning in Sorted Order (BACK-TO-FRONT) ────────────────────────
            // Higher depth = further away -> Spawn FIRST
            renderQueue.sortByDescending { it.depth }
            val decorationsEntities = mutableListOf<WrapperEntity>()
            val layerEntities = mutableListOf<WrapperEntity>()

            renderQueue.forEach { task ->
                val entity = task.spawn()
                if (entity != null) {
                    // Correctly track based on the depth/type (simplified by order, but still useful for session cleanup)
                    if (task.depth >= fwd + 0.015) layerEntities.add(entity)
                    else if (task.depth >= fwd + 0.005) decorationsEntities.add(entity)
                }
            }

            // --- Start session ---
            val session = MenuSessionService.Session(
                player = player,
                menuId = UUID.randomUUID().toString(),
                buttons = sessionButtons.toMutableList(),
                decorations = decorationsEntities,
                decorationConfigs = decorations,
                layers = layerEntities,
                originalLocation = originalLoc,
                cameraLocation = cameraLoc,
                closeOnMove = closeOnMove,
                screenScale = screen.screenScale,
                forwardDistance = fwd,
                cursorSensitivity = cursor.sensitivity,
                cursorSmoothing = cursor.smoothing,
                playerFov = playerFov,
                openActions = onOpenActions,
                closeActions = onCloseActions,
                safeMode = safeMode,
                infoAreaEntity = infoAreaEntity,
                cursorXMin = cursor.xMin,
                cursorXMax = cursor.xMax,
                cursorYMin = cursor.yMin,
                cursorYMax = cursor.yMax,
                cursorItem = cursor.item.get(player, context),
                cursorScale = cursor.scale,
                fovCalibration = calibration,
                linkConfigs = links,
                baseForwardDistance = screen.forwardDistance,
                basis = basis,
                spawnYaw = cameraLoc.yaw,
                spawnPitch = cameraLoc.pitch,
                cursorX = 50.0,
                cursorY = 50.0,
                targetCursorX = 50.0,
                targetCursorY = 50.0,
                grabPanorama = screen.grabPanorama,
                yawSync = screen.yawSync,
                loop = screen.loop,
                invisibilityMode = screen.invisibilityMode
            )

            sessionService.startSession(player, session)
            
            // Fix: Only play opening animation if explicitly enabled in config (prevents global background animations)
            if (screen.animateOpening) {
                sessionService.startAnimation(session)
            }
        }

    }
}
