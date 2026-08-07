package com.saarthi.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.saarthi.ui.theme.SaarthiColors

/**
 * The seven Lucide (lucide.dev, ISC license) icons the design uses, hand-built
 * as [ImageVector]s rather than pulled in via material-icons-extended (a new
 * dependency for the wrong icon language) or res/drawable (harder to tint
 * per-caller). Paths are transcribed from the original mockups' inlined SVGs:
 * viewBox 0 0 24 24, fill=none, stroke=currentColor, stroke-width=2,
 * round caps/joins. The stroke color set here is a placeholder — callers
 * always pass `tint` to `Icon(...)`, which recolors via a SrcIn color
 * filter over the stroke's alpha, so the placeholder value never shows.
 */
object SaarthiIcons {

    private val placeholder = SolidColor(Color.Black)

    val Mic: ImageVector by lazy {
        ImageVector.Builder(
            name = "Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = placeholder,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                // Stand: base + the "U" cup below the capsule
                moveTo(12f, 19f)
                lineTo(12f, 22f)
                moveTo(19f, 10f)
                lineTo(19f, 12f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 5f, y1 = 12f)
                lineTo(5f, 10f)
            }
            path(
                fill = null,
                stroke = placeholder,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Capsule: rect x=9 y=2 w=6 h=13 rx=3 ry=3
                moveTo(15f, 5f)
                lineTo(15f, 12f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 9f, y1 = 12f)
                lineTo(9f, 5f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 15f, y1 = 5f)
                close()
            }
        }.build()
    }

    val Menu: ImageVector by lazy {
        ImageVector.Builder(
            name = "Menu",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = placeholder,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 6f); lineTo(20f, 6f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 18f); lineTo(16f, 18f) // shortened third bar, per the original design
            }
        }.build()
    }

    val SlidersHorizontal: ImageVector by lazy {
        ImageVector.Builder(
            name = "SlidersHorizontal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = placeholder,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(21f, 4f); lineTo(14f, 4f)
                moveTo(10f, 4f); lineTo(3f, 4f)
                moveTo(21f, 12f); lineTo(12f, 12f)
                moveTo(8f, 12f); lineTo(3f, 12f)
                moveTo(21f, 20f); lineTo(16f, 20f)
                moveTo(12f, 20f); lineTo(3f, 20f)
                moveTo(14f, 2f); lineTo(14f, 6f)
                moveTo(8f, 10f); lineTo(8f, 14f)
                moveTo(16f, 18f); lineTo(16f, 22f)
            }
        }.build()
    }

    val ArrowLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowLeft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = placeholder,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f)
                moveTo(19f, 12f); lineTo(5f, 12f)
            }
        }.build()
    }

    val ArrowRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = placeholder,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12f); lineTo(19f, 12f)
                moveTo(12f, 5f); lineTo(19f, 12f); lineTo(12f, 19f)
            }
        }.build()
    }

    /**
     * The app's brand mark: a ring, a rail, and the stair line it runs
     * beside. Multi-color (unlike the other icons here), so render with
     * [androidx.compose.foundation.Image], not [androidx.compose.material3.Icon]
     * — Icon's tint would flatten it.
     */
    val Logo: ImageVector by lazy {
        ImageVector.Builder(
            name = "SaarthiLogo",
            defaultWidth = 96.dp,
            defaultHeight = 96.dp,
            viewportWidth = 120f,
            viewportHeight = 120f,
        ).apply {
            // Ring
            path(fill = null, stroke = SolidColor(SaarthiColors.Accent), strokeLineWidth = 1.5f) {
                moveTo(18f, 60f)
                arcTo(42f, 42f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 102f, y1 = 60f)
                arcTo(42f, 42f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 18f, y1 = 60f)
            }
            // Rail underside — two faint parallel lines
            path(fill = null, stroke = SolidColor(SaarthiColors.Neutral400), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
                moveTo(32f, 70.9f); lineTo(74f, 45.7f); lineTo(88.5f, 45.7f)
            }
            path(fill = null, stroke = SolidColor(SaarthiColors.Neutral400), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round) {
                moveTo(32f, 75.5f); lineTo(74f, 50.3f); lineTo(88.5f, 50.3f)
            }
            // Stair line
            path(
                fill = null,
                stroke = SolidColor(SaarthiColors.Text),
                strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(34f, 79f); lineTo(54f, 79f); lineTo(54f, 67f); lineTo(74f, 67f); lineTo(74f, 55f); lineTo(90f, 55f)
            }
            // Rail top + posts
            path(fill = null, stroke = SolidColor(SaarthiColors.Accent), strokeLineWidth = 3.5f, strokeLineCap = StrokeCap.Round) {
                moveTo(31f, 66.8f); lineTo(74f, 41f); lineTo(90f, 41f)
            }
            path(fill = null, stroke = SolidColor(SaarthiColors.Accent), strokeLineWidth = 2.5f, strokeLineCap = StrokeCap.Round) {
                moveTo(34f, 65f); lineTo(34f, 76.5f)
            }
            path(fill = null, stroke = SolidColor(SaarthiColors.Accent), strokeLineWidth = 2.5f, strokeLineCap = StrokeCap.Round) {
                moveTo(87f, 41f); lineTo(87f, 52.5f)
            }
        }.build()
    }

    /** The "Heard you" check badge on [com.saarthi.ui.screens.InvokeSheet]'s second state. */
    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = placeholder,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f)
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "X",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = placeholder,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(18f, 6f); lineTo(6f, 18f)
                moveTo(6f, 6f); lineTo(18f, 18f)
            }
        }.build()
    }
}
