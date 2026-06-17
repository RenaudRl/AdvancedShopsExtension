package btcrenaud.advancedmenus.api

import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.entry.entries.ConstVar
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.utils.Color
import btcrenaud.advancedmenus.services.TextDisplayService
import com.typewritermc.engine.paper.entry.entries.ActionEntry
import com.typewritermc.core.entries.Ref
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.utils.Sound

data class MenuScreen(
    @Help("Forward distance from camera in blocks")
    val forwardDistance: Double = 1.0,
    @Help("Screen scale multiplier (applied to all elements)")
    val screenScale: Double = 1.0,
    @Help("Optional info area for hovered button lore")
    val infoArea: InfoArea? = null,
    @Help("Mouse wheel interaction behavior (Scroll/Zoom)")
    val wheelInteraction: WheelInteractionConfig = WheelInteractionConfig(),
    @Help("Whether to animate menu opening (bottom-to-top interpolation)")
    val animateOpening: Boolean = false,
    @Help("Enable automatic UI scaling based on FOV (makes items larger for higher FOVs)")
    val fovScalingEnabled: Boolean = true,
    @Help("Grab panorama mode: allows mouse interaction to rotate view")
    val grabPanorama: Boolean = false,
    @Help("Synchronize yaw across sessions")
    val yawSync: Boolean = false,
    @Help("Infinite loop for panorama")
    val loop: Boolean = false,
    @Help("Invisibility mode for the player during camera session")
    val invisibilityMode: InvisibilityMode = InvisibilityMode.GLOBAL_DEFAULT,
    @Help("Static brightness override for blocks (0-15, null = use global)")
    val brightnessBlock: Int? = null,
    @Help("Static brightness override for sky (0-15, null = use global)")
    val brightnessSky: Int? = null,
    @Help("Enable debug mode to visualize hitboxes with particles")
    val debug: Boolean = false
) {
    @Help("Actions to execute when the menu is opened")
    var openActions: List<Ref<ActionEntry>> = emptyList()

    @Help("Actions to execute when the menu is closed")
    var closeActions: List<Ref<ActionEntry>> = emptyList()
}

enum class InvisibilityMode {
    GLOBAL_DEFAULT,
    FORCE_VISIBLE,
    FORCE_INVISIBLE
}

data class InfoArea(
    val anchorX: Double = 50.0,
    val anchorY: Double = 90.0,
    val scale: Double = 0.8,
    val layer: Int = 5,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val lineWidth: Int = 200,
    val backgroundColor: Color = Color.BLACK_BACKGROUND,
    val backgroundAlpha: Double = 0.7,
    @Help("Static brightness (0-15)")
    val brightnessBlock: Int = 15,
    @Help("Static sky brightness (0-15)")
    val brightnessSky: Int = 15
)

data class TextMenuButton(
    @Help("Unique button identifier")
    val name: String = "",
    @Help("Enable/disable this button")
    val enabled: Boolean = true,
    @Help("Text content")
    @Placeholder
    @MultiLine
    val content: String = "Button",
    @Help("Anchor X (0-100)")
    val anchorX: Double = 50.0,
    @Help("Anchor Y (0-100)")
    val anchorY: Double = 50.0,
    @Help("Horizontal offset")
    val offsetX: Double = 0.0,
    @Help("Vertical offset")
    val offsetY: Double = 0.0,
    @Help("Scale multiplier")
    val scale: Double = 1.0,
    @Help("Visual layer (Z-order)")
    val layer: Int = 1,
    @Help("Text alignment (LEFT, CENTER, RIGHT)")
    val alignment: TextAlignment = TextAlignment.CENTER,
    @Help("Opacity (0.0 - 1.0)")
    val textOpacity: Double = 1.0,
    @Help("Background color")
    val backgroundColor: Color = Color.BLACK_BACKGROUND,
    @Help("Background transparency (0.0 - 1.0)")
    val backgroundAlpha: Double = 0.0,
    @Help("Whether to billboard")
    val faceCamera: Boolean = false,
    @Help("Criteria to show this button")
    val criteria: List<Criteria> = emptyList(),
    @Help("Close the menu when clicked")
    val stopMenu: StopMenuSettings = StopMenuSettings(),
    @Help("Hover effects")
    val hoverEffects: List<HoverEffect> = emptyList(),
    @Help("Hover sound")
    val hoverSound: com.typewritermc.engine.paper.utils.Sound = com.typewritermc.engine.paper.utils.Sound.EMPTY,
    @Help("Click sound")
    val clickSound: com.typewritermc.engine.paper.utils.Sound = com.typewritermc.engine.paper.utils.Sound.EMPTY,
    @Help("Lore shown in info area")
    @Placeholder
    @MultiLine
    val lore: String = "",
    @Help("Click priority (high = on top)")
    val priority: Int = 0,
    @Help("Max line width")
    val lineWidth: Int = 200,
    @Help("Whether the text should have a shadow")
    val shadow: Boolean = false,
    @Help("Whether the text is visible through blocks")
    val seeThroughBlocks: Boolean = false,
    @Help("Parent node ID for automatic skill tree linking")
    val parent: String? = null,
    @Help("Alternative states based on criteria")
    val states: List<ButtonState> = emptyList(),
    @Help("Item to follow (relative coordinates)")
    val relativeTo: String? = null,
    @Help("Static brightness (0-15)")
    val brightnessBlock: Int = 15,
    @Help("Static sky brightness (0-15)")
    val brightnessSky: Int = 15,
    @Help("Optional ID of a DecorationElement configured in advanced_base_menu to link with this button's hover state")
    val linkedDecorationId: String? = null,
    @Help("Scale multiplier to apply to the linked decoration when button is hovered")
    val linkedDecorationHoverScale: Double = 1.1
) {
    @Help("Actions on click")
    var actions: List<Ref<ActionEntry>> = emptyList()

    @Help("Actions on right-click")
    var rightClickActions: List<Ref<ActionEntry>> = emptyList()
}

data class InputMenuButton(
    @Help("Input identifier")
    val name: String = "",
    @Help("Enable/disable this button")
    val enabled: Boolean = true,
    @Help("Initial value")
    @Placeholder
    val initialValue: String = "",
    @Help("Placeholder when empty")
    val placeholder: String = "Type here...",
    @Help("Anchor X (0-100)")
    val anchorX: Double = 50.0,
    @Help("Anchor Y (0-100)")
    val anchorY: Double = 50.0,
    @Help("Horizontal offset")
    val offsetX: Double = 0.0,
    @Help("Vertical offset")
    val offsetY: Double = 0.0,
    @Help("Scale multiplier")
    val scale: Double = 1.0,
    @Help("Visual layer")
    val layer: Int = 1,
    @Help("Text alignment")
    val alignment: TextAlignment = TextAlignment.CENTER,
    @Help("Background color")
    val backgroundColor: Color = Color.BLACK_BACKGROUND,
    @Help("Background transparency")
    val backgroundAlpha: Double = 0.0,
    @Help("Whether to billboard")
    val faceCamera: Boolean = false,
    @Help("Text opacity (0.0 - 1.0)")
    val textOpacity: Double = 1.0,
    @Help("Line wrapping width")
    val lineWidth: Int = 200,
    @Help("Drop shadow")
    val shadow: Boolean = false,
    @Help("See through blocks")
    val seeThroughBlocks: Boolean = false,
    @Help("Criteria to show")
    val criteria: List<Criteria> = emptyList(),
    @Help("Hover effects")
    val hoverEffects: List<HoverEffect> = emptyList(),
    @Help("Hover sound")
    val hoverSound: com.typewritermc.engine.paper.utils.Sound = com.typewritermc.engine.paper.utils.Sound.EMPTY,
    @Help("Click sound")
    val clickSound: com.typewritermc.engine.paper.utils.Sound = com.typewritermc.engine.paper.utils.Sound.EMPTY,
    @Help("Lore shown in info area")
    @Placeholder
    @MultiLine
    val lore: String = "",
    @Help("Click priority")
    val priority: Int = 0
)

data class ItemMenuButton(
    @Help("Unique button identifier")
    val name: String = "",
    @Help("Enable/disable this button")
    val enabled: Boolean = true,
    @Help("Item to display")
    val item: Var<Item> = ConstVar(Item.Empty),
    @Help("Anchor X (0-100)")
    val anchorX: Double = 50.0,
    @Help("Anchor Y (0-100)")
    val anchorY: Double = 50.0,
    @Help("Horizontal offset")
    val offsetX: Double = 0.0,
    @Help("Vertical offset")
    val offsetY: Double = 0.0,
    @Help("Scale multiplier")
    val scale: Double = 1.0,
    @Help("Visual layer")
    val layer: Int = 1,
    @Help("Whether to billboard")
    val faceCamera: Boolean = false,
    @Help("Criteria to show")
    val criteria: List<Criteria> = emptyList(),
    @Help("Close the menu when clicked")
    val stopMenu: StopMenuSettings = StopMenuSettings(),
    @Help("Hover effects")
    val hoverEffects: List<HoverEffect> = emptyList(),
    @Help("Hover sound")
    val hoverSound: com.typewritermc.engine.paper.utils.Sound = com.typewritermc.engine.paper.utils.Sound.EMPTY,
    @Help("Click sound")
    val clickSound: com.typewritermc.engine.paper.utils.Sound = com.typewritermc.engine.paper.utils.Sound.EMPTY,
    @Help("Lore shown in info area")
    @Placeholder
    @MultiLine
    val lore: String = "",
    @Help("Click priority")
    val priority: Int = 0,
    @Help("Depth offset from plane (negative = closer to camera)")
    val depthOffset: Double = 0.0,
    @Help("Static rotation around X axis")
    val rotationX: Double = 0.0,
    @Help("Static rotation around Y axis")
    val rotationY: Double = 0.0,
    @Help("Static rotation around Z axis")
    val rotationZ: Double = 0.0,
    @Help("Optional ID of a DecorationElement configured in advanced_base_menu to link with this button's hover state")
    val linkedDecorationId: String? = null,
    @Help("Scale multiplier to apply to the linked decoration when button is hovered")
    val linkedDecorationHoverScale: Double = 1.1,
    @Help("Alternative states based on criteria")
    val states: List<ButtonState> = emptyList(),
    @Help("Parent node ID for automatic skill tree linking")
    val parent: String? = null,
    @Help("Item to follow (relative coordinates)")
    val relativeTo: String? = null,
    @Help("Static brightness (0-15)")
    val brightnessBlock: Int = 15,
    @Help("Static sky brightness (0-15)")
    val brightnessSky: Int = 15
) {
    @Help("Actions on click")
    var actions: List<Ref<ActionEntry>> = emptyList()

    @Help("Actions on right-click")
    var rightClickActions: List<Ref<ActionEntry>> = emptyList()
}

data class MenuLayer(
    @Help("Text content for the layer")
    @Placeholder
    @MultiLine
    val content: String = "",
    @Help("Image URL to display (overrides content)")
    val imageUrl: String = "",
    @Help("Anchor X (0-100)")
    val anchorX: Double = 50.0,
    @Help("Anchor Y (0-100)")
    val anchorY: Double = 50.0,
    @Help("Horizontal offset")
    val offsetX: Double = 0.0,
    @Help("Vertical offset")
    val offsetY: Double = 0.0,
    @Help("Scale multiplier")
    val scale: Double = 1.0,
    @Help("Visual layer (Z-order)")
    val layer: Int = 0,
    @Help("Text alignment (LEFT, CENTER, RIGHT)")
    val alignment: TextAlignment = TextAlignment.CENTER,
    @Help("Max line width")
    val lineWidth: Int = 200,
    @Help("Text opacity (0.0 - 1.0)")
    val textOpacity: Double = 1.0,
    @Help("Whether the layer should face the camera (billboarding)")
    val faceCamera: Boolean = false,
    @Help("Background color")
    val backgroundColor: Color = Color.BLACK_BACKGROUND,
    @Help("Background transparency")
    val backgroundAlpha: Double = 0.0,
    @Help("Whether the text should have a shadow")
    val shadow: Boolean = false,
    @Help("Whether the text is visible through blocks")
    val seeThroughBlocks: Boolean = false,
    @Help("Static brightness (0-15)")
    val brightnessBlock: Int = 15,
    @Help("Static sky brightness (0-15)")
    val brightnessSky: Int = 15
)

data class DecorationElement(
    @Help("Unique identifier for this decoration (used by linkedDecorationId in buttons)")
    val id: String = "",
    @Help("Text content or Item (leave content empty if using item)")
    @Placeholder
    @MultiLine
    val content: String = "",
    @Help("Item to display")
    val item: Var<Item> = ConstVar(Item.Empty),
    @Help("Anchor X (0-100)")
    val anchorX: Double = 50.0,
    @Help("Anchor Y (0-100)")
    val anchorY: Double = 50.0,
    @Help("Horizontal offset")
    val offsetX: Double = 0.0,
    @Help("Vertical offset")
    val offsetY: Double = 0.0,
    @Help("Scale multiplier")
    val scale: Double = 1.0,
    @Help("Visual layer (Z-order)")
    val layer: Int = 0,
    @Help("Text alignment")
    val alignment: TextAlignment = TextAlignment.CENTER,
    @Help("Text opacity (0.0 - 1.0)")
    val textOpacity: Double = 1.0,
    @Help("Whether to billboard")
    val faceCamera: Boolean = false,
    @Help("Background color (for text)")
    val backgroundColor: Color = Color.BLACK_BACKGROUND,
    @Help("Background transparency (for text)")
    val backgroundAlpha: Double = 0.0,
    @Help("Whether the text should have a shadow")
    val shadow: Boolean = false,
    @Help("Whether the text is visible through blocks")
    val seeThroughBlocks: Boolean = false,
    @Help("Rotation X (for item)")
    val rotationX: Double = 0.0,
    @Help("Rotation Y (for item)")
    val rotationY: Double = 0.0,
    @Help("Rotation Z (for item)")
    val rotationZ: Double = 0.0,
    @Help("Static brightness (0-15)")
    val brightnessBlock: Int = 15,
    @Help("Static sky brightness (0-15)")
    val brightnessSky: Int = 15,
    @Help("Parent node ID for automatic skill tree linking")
    val parent: String? = null
) {
    val isItem: Boolean get() = content.isEmpty() 
}

data class MenuCursor(
    @Help("Cursor sensitivity")
    val sensitivity: Double = 1.0,
    @Help("Cursor smoothing factor")
    val smoothing: Double = 0.5,
    @Help("Minimum X position")
    val xMin: Double = 0.0,
    @Help("Maximum X position")
    val xMax: Double = 100.0,
    @Help("Minimum Y position")
    val yMin: Double = 0.0,
    @Help("Maximum Y position")
    val yMax: Double = 100.0,
    @Help("Cursor icon item")
    val item: Var<Item> = ConstVar(Item.Empty),
    @Help("Cursor scale multiplier")
    val scale: Double = 1.0,
    @Help("Static brightness (0-15)")
    val brightnessBlock: Int = 15,
    @Help("Static sky brightness (0-15)")
    val brightnessSky: Int = 15
)

data class HoverEffect(
    @Help("Type of hover effect (SCALE, MOVE, TEXT_SWAP, etc.)")
    val type: HoverEffectType = HoverEffectType.SCALE,
    @Help("Target scale multiplier (for SCALE effect)")
    val targetScale: Double = 1.1,
    @Help("Duration in ticks for interpolation")
    val durationTicks: Int = 5,
    @Help("Horizontal movement offset (for MOVE effect)")
    val offsetX: Double = 0.0,
    @Help("Vertical movement offset (for MOVE effect)")
    val offsetY: Double = 0.0,
    @Help("Alternative text content (for TEXT_SWAP)")
    val hoverText: String = "",
    @Help("Original text content (for TEXT_SWAP restoration)")
    val normalText: String = "",
    @Help("Target opacity 0.0 to 1.0 (for OPACITY effect)")
    val targetOpacity: Double = 1.0,
    @Help("Target background color (for COLOR effect)")
    val targetColor: Color = Color.WHITE
)

enum class HoverEffectType {
    SCALE,
    MOVE,
    TEXT_SWAP,
    OPACITY,
    COLOR
}

data class StopMenuSettings(
    @Help("Whether to close the menu on click")
    val enabled: Boolean = false,
    @Help("Whether to teleport the player back to their original location")
    val restoreLocation: Boolean = true
)

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}

enum class WheelActionType {
    NONE,
    SCROLL_VERTICAL,
    SCROLL_HORIZONTAL,
    ZOOM
}

data class HotbarInteractionConfig(
    @Help("Actions triggered on forward step (9->1)")
    val onStepForward: List<Ref<ActionEntry>> = emptyList(),
    @Help("Actions triggered on backward step (1->9)")
    val onStepBackward: List<Ref<ActionEntry>> = emptyList(),
    @Help("Actions triggered on any wheel cycle")
    val onCycle: List<Ref<ActionEntry>> = emptyList()
)

data class ScrollZone(
    @Help("Anchor X start (0-100)") val anchorX: Double = 0.0,
    @Help("Anchor Y start (0-100)") val anchorY: Double = 0.0,
    @Help("Width of the zone in screen units") val width: Double = 100.0,
    @Help("Height of the zone in screen units") val height: Double = 100.0,
    @Help("Number of horizontal pages (multiplicative scrolling)") val pagesX: Int = 1,
    @Help("Number of vertical pages (multiplicative scrolling)") val pagesY: Int = 1,
    @Help("Whether to fade out elements when leaving the zone") val fadeOut: Boolean = true,
    @Help("Whether to enforce strict clipping (invisible and non-interactable outside)") val clip: Boolean = true,
    @Help("Virtual content width (for sub-zone perspective)") val virtualWidth: Double? = null,
    @Help("Virtual content height (for sub-zone perspective)") val virtualHeight: Double? = null,
    @Help("Unique ID for this zone") val id: String = "default"
)

data class ZoomZone(
    @Help("X center of zoom (0-100)") val centerX: Double = 50.0,
    @Help("Y center of zoom (0-100)") val centerY: Double = 50.0,
    @Help("Radius of effect in screen units") val radius: Double = 100.0,
    @Help("Maximum scale multiplier") val maxScale: Double = 5.0,
    @Help("Whether this zoom is absolute (applied to everything equally)") val isAbsolute: Boolean = false,
    @Help("Unique ID for this zone") val id: String = "default"
)

data class WheelInteractionConfig(
    @Help("Type of interaction (NONE, SCROLL_VERTICAL, SCROLL_HORIZONTAL, ZOOM)")
    val type: WheelActionType = WheelActionType.NONE,
    @Help("Step multiplier per wheel tick (speed of scroll/zoom)")
    val stepMultiplier: Double = 5.0,
    @Help("Minimum boundary/scale limit")
    val minBoundary: Double = -500.0,
    @Help("Maximum boundary/scale limit")
    val maxBoundary: Double = 500.0,
    @Help("Whether to zoom/scroll instantly without smooth interpolation (Performance mode)")
    val instantTransform: Boolean = false,
    @Help("Whether to bounce back when hitting boundaries")
    val bounce: Boolean = false,
    @Help("List of specific scroll zones")
    val scrollZones: List<ScrollZone> = emptyList(),
    @Help("List of specific zoom zones")
    val zoomZones: List<ZoomZone> = emptyList(),
    @Help("Hotbar gesture configuration")
    val hotbar: HotbarInteractionConfig = HotbarInteractionConfig()
)

data class ButtonState(
    @Help("Priority (higher value takes precedence)")
    val priority: Int = 0,
    @Help("Criteria to enable this state")
    val criteria: List<Criteria> = emptyList(),
    @Help("Display text override")
    val content: String? = null,
    @Help("Display item override")
    val item: Var<Item>? = null,
    @Help("Click actions override")
    val actions: List<Ref<ActionEntry>>? = null,
    @Help("Visibility override")
    val enabled: Boolean? = null
)

data class SkillTreeLink(
    @Help("ID of the source button/element")
    val fromId: String = "",
    @Help("ID of the target button/element")
    val toId: String = "",
    @Help("Visual style of the link")
    val style: LinkStyle = LinkStyle(),
    @Help("Criteria to show this link (e.g. both nodes must be discovered)")
    val criteria: List<Criteria> = emptyList(),
    @Help("Criteria to show the arrowhead (if configured in style)")
    val arrowCriteria: List<Criteria> = emptyList(),
    @Help("Alternative states based on criteria")
    val states: List<LinkState> = emptyList()
)

data class LinkStyle(
    @Help("Color of the line")
    val color: Color = Color.WHITE,
    @Help("Thickness/Scale of the line")
    val thickness: Double = 1.0,
    @Help("Whether to animate the line (e.g. flowing effect)")
    val animated: Boolean = false,
    @Help("Material for the line (e.g. WHITE_CONCRETE or more specific particles)")
    val material: String = "WHITE_CONCRETE",
    @Help("Z-Layer offset for the line")
    val layer: Int = 0,
    @Help("Margin in anchor units. The link will stay this distance away from the node edges.")
    val margin: Double = 1.0,
    @Help("Arrowhead configuration (optional)")
    val arrowhead: LinkArrowheadConfig? = null
)

data class LinkArrowheadConfig(
    @Help("Material for the arrowhead")
    val material: String = "PAPER",
    @Help("Custom model data for the arrowhead item")
    val customModelData: Int? = null,
    @Help("Scale multiplier for the arrowhead")
    val scale: Double = 0.5,
    @Help("Additional layer offset (added to link layer)")
    val layerOffset: Double = 0.1
)

data class LinkState(
    @Help("Priority (higher value takes precedence)")
    val priority: Int = 0,
    @Help("Criteria to enable this state")
    val criteria: List<Criteria> = emptyList(),
    @Help("Style override")
    val style: LinkStyle? = null
)

data class CardMenuButton(
    val name: String = "",
    @Help("Enable/disable this card")
    val enabled: Boolean = true,
    @Help("Item shown on the back of the card (face down)")
    val backItem: Var<Item> = ConstVar(Item.Empty),
    @Help("Item shown on the front of the card (face up / reward)")
    val frontItem: Var<Item> = ConstVar(Item.Empty),
    @Help("Anchor X (0-100)")
    val anchorX: Double = 50.0,
    @Help("Anchor Y (0-100)")
    val anchorY: Double = 50.0,
    @Help("Horizontal offset")
    val offsetX: Double = 0.0,
    @Help("Vertical offset")
    val offsetY: Double = 0.0,
    @Help("Scale multiplier")
    val scale: Double = 1.0,
    @Help("Visual layer")
    val layer: Int = 1,
    @Help("Whether to billboard the card")
    val faceCamera: Boolean = true,
    @Help("Criteria to show")
    val criteria: List<Criteria> = emptyList(),
    @Help("Close the menu when all cards revealed")
    val stopMenu: StopMenuSettings = StopMenuSettings(),
    @Help("Hover sound")
    val hoverSound: com.typewritermc.engine.paper.utils.Sound = com.typewritermc.engine.paper.utils.Sound.EMPTY,
    @Help("Click sound")
    val clickSound: com.typewritermc.engine.paper.utils.Sound = com.typewritermc.engine.paper.utils.Sound.EMPTY,
    @Help("Click priority")
    val priority: Int = 0,
    @Help("Depth offset from plane")
    val depthOffset: Double = 0.0,
    @Help("Static rotation around X axis")
    val rotationX: Double = 0.0,
    @Help("Static rotation around Y axis")
    val rotationY: Double = 0.0,
    @Help("Static rotation around Z axis")
    val rotationZ: Double = 0.0,
    @Help("Duration of the flip animation in ticks")
    val flipDuration: Int = 10,
    @Help("Alternative states based on criteria")
    val states: List<ButtonState> = emptyList(),
    @Help("Static brightness (0-15)")
    val brightnessBlock: Int = 15,
    @Help("Static sky brightness (0-15)")
    val brightnessSky: Int = 15
) {
    @Help("Actions on reveal (when card is flipped)")
    var actions: List<Ref<ActionEntry>> = emptyList()

    @Help("Actions on right-click")
    var rightClickActions: List<Ref<ActionEntry>> = emptyList()
}
