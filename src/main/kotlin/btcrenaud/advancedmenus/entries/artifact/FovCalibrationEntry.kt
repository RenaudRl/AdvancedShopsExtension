package btcrenaud.advancedmenus.entries.artifact

import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry
import com.typewritermc.core.entries.Ref

/**
 * Artifact entry that defines 3D projection calibration settings for Advanced Menus.
 * This allows fine-tuning how menus scale and offset at different FOVs.
 */
@Entry("fov_calibration", "FOV Calibration settings", com.typewritermc.core.books.pages.Colors.BLUE, "mdi:aspect-ratio")
@Tags("fov")
class FovCalibrationEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Base FOV for which the initial layout was designed (typically 70.0)")
    val baseFov: Double = 70.0,
    @Help("Base Aspect Ratio at the base FOV (typically 2.08 for widescreen)")
    val baseAspect: Double = 2.08,
    @Help("Correction factor for high FOVs (0.0 = no horizontal contraction, 0.4 = strong contraction)")
    val correctionFactor: Double = 0.35
) : ArtifactEntry {
    override val artifactId: String get() = id
}
