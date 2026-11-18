package com.anilili.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anilili.data.settings.SettingsStore
import com.anilili.ui.adaptive.LocalAppDeviceProfile

/*
 * Liquid glass: the frosted glass the app already draws, finished the way light actually treats a
 * thick pane of it. A bright specular line runs round the edge and is strongest where a lamp at the
 * top left would catch it (and, faintly, the far corner); a soft glow sits just inside that line;
 * the top of the pane carries a sheen that fades away; a thin bright lip gathers along the bottom
 * edge; and a faint caustic crosses the body corner to corner.
 *
 * It is a style laid over [GlassSection]'s blur, not a second blur: it draws only where Haze has
 * already drawn glass, so a part whose glass is switched off stays flat, and it carries the same
 * gate — Android 12+, a phone, and the switch in Appearance.
 *
 * Everything here is a plain gradient, drawn once per frame inside a clip the caller already owns.
 * There is no extra offscreen layer and no per-frame allocation beyond the gradient shaders.
 */

/** Whether the liquid look is on right now. Same gate as [glassEnabled], plus its own switch. */
@Composable
fun liquidGlassEnabled(): Boolean {
    val on by SettingsStore.liquidGlass.collectAsState()
    return on && backdropBlurAvailable && !LocalAppDeviceProfile.current.isTv
}

/** White with no opacity: a gradient fades through this rather than through transparent black. */
private val Clear = Color.White.copy(alpha = 0f)

/** The edge light, as a lamp at the top left would leave it: bright there, dim along the sides. */
private val RimDiagonal: Brush = Brush.linearGradient(
    0.00f to Color.White.copy(alpha = 0.90f),
    0.20f to Color.White.copy(alpha = 0.22f),
    0.50f to Color.White.copy(alpha = 0.08f),
    0.80f to Color.White.copy(alpha = 0.24f),
    1.00f to Color.White.copy(alpha = 0.70f),
)

/** The same light, spread a little inside the edge so the rim glows rather than just outlines. */
private val GlowDiagonal: Brush = Brush.linearGradient(
    0.00f to Color.White.copy(alpha = 0.34f),
    0.20f to Color.White.copy(alpha = 0.08f),
    0.50f to Clear,
    0.80f to Color.White.copy(alpha = 0.10f),
    1.00f to Color.White.copy(alpha = 0.26f),
)

/** For panes that run off the bottom of the screen: lit along the top edge only. */
private val RimTop: Brush = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.75f),
    0.06f to Color.White.copy(alpha = 0.20f),
    0.22f to Color.White.copy(alpha = 0.05f),
    1.00f to Clear,
)

private val GlowTop: Brush = Brush.verticalGradient(
    0.00f to Color.White.copy(alpha = 0.30f),
    0.08f to Color.White.copy(alpha = 0.06f),
    0.20f to Clear,
    1.00f to Clear,
)

/**
 * The body of the glass: a faint lift, a caustic across the diagonal, the sheen at the top and the
 * lip along the bottom. Drawn behind the content, so icons and text sit on top of it. It does not
 * clip itself — the caller's own clip (which Haze needs anyway) keeps it inside the shape.
 */
fun Modifier.liquidGlassSheen(): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@drawBehind

    // A whisper of white over the whole pane, so it reads as lit from within rather than as a hole.
    drawRect(Color.White.copy(alpha = 0.04f))

    // The caustic: light bent through the thick glass, landing at the two opposite corners.
    drawRect(
        Brush.linearGradient(
            0.00f to Color.White.copy(alpha = 0.13f),
            0.38f to Clear,
            0.64f to Clear,
            1.00f to Color.White.copy(alpha = 0.09f),
        ),
    )

    // The sheen. Capped in height so a tall sheet gets a highlight, not a white wash.
    val sheenHeight = minOf(h * 0.62f, 120.dp.toPx())
    drawRect(
        Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = 0.22f),
            0.50f to Color.White.copy(alpha = 0.06f),
            1.00f to Clear,
            startY = 0f,
            endY = sheenHeight,
        ),
        size = Size(w, sheenHeight),
    )

    // The lip: light collecting along the lower edge of a thick pane.
    val lipHeight = minOf(h * 0.24f, 14.dp.toPx())
    drawRect(
        Brush.verticalGradient(
            0.00f to Clear,
            1.00f to Color.White.copy(alpha = 0.11f),
            startY = h - lipHeight,
            endY = h,
        ),
        topLeft = Offset(0f, h - lipHeight),
        size = Size(w, lipHeight),
    )
}

/**
 * The edge: a soft glow just inside it and the bright specular line on it. Drawn last so it sits
 * over everything inside. [topLit] is for sheets, which run off the screen and are lit along the
 * top only.
 */
fun Modifier.liquidGlassRim(
    shape: Shape,
    width: Dp = 1.dp,
    topLit: Boolean = false,
): Modifier = this
    .drawBehind {
        if (size.minDimension <= 0f) return@drawBehind
        val path = shape.createOutline(size, layoutDirection, this).toPath()
        // A stroke is centred on the edge, so half of this one is outside the shape: clipped away,
        // which leaves the inner half as the glow.
        clipPath(path) {
            drawPath(path, if (topLit) GlowTop else GlowDiagonal, style = Stroke(width = 7.dp.toPx()))
        }
    }
    .border(width, if (topLit) RimTop else RimDiagonal, shape)

/** Sheen and rim together: everything the liquid look adds to one pane of glass. */
fun Modifier.liquidGlass(shape: Shape, topLit: Boolean = false): Modifier =
    liquidGlassSheen().liquidGlassRim(shape, topLit = topLit)

/**
 * [liquidGlass] when the switch is on, nothing when it is off, and nothing at all for a null
 * [shape]. For the places that build a modifier chain outside composition and so cannot read the
 * setting themselves.
 */
fun Modifier.liquidGlassIfOn(shape: Shape?): Modifier =
    if (shape == null) {
        this
    } else {
        composed { if (liquidGlassEnabled()) Modifier.liquidGlass(shape) else Modifier }
    }

/**
 * Gloss for the open tab's pill, drawn over its accent fill: a bright bloom across the top, a pool
 * of light along the bottom, and a fine rim. [progress] is the pill's own open/closed value, so
 * all of it grows in and out with the pill instead of popping.
 */
internal fun DrawScope.drawLiquidPill(progress: Float, rim: Boolean = true) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f || progress <= 0f) return

    val bloom = h * 0.62f
    drawRect(
        Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = 0.40f * progress),
            0.55f to Color.White.copy(alpha = 0.08f * progress),
            1.00f to Clear,
            startY = 0f,
            endY = bloom,
        ),
        size = Size(w, bloom),
    )

    val pool = h * 0.38f
    drawRect(
        Brush.verticalGradient(
            0.00f to Clear,
            1.00f to Color.White.copy(alpha = 0.20f * progress),
            startY = h - pool,
            endY = h,
        ),
        topLeft = Offset(0f, h - pool),
        size = Size(w, pool),
    )

    // A pill rounds to half its height; anything with other corners draws its own rim instead.
    if (!rim) return

    val stroke = 1.dp.toPx()
    drawRoundRect(
        Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = 0.75f * progress),
            1.00f to Color.White.copy(alpha = 0.20f * progress),
        ),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(w - stroke, h - stroke),
        cornerRadius = CornerRadius(h / 2f),
        style = Stroke(stroke),
    )
}

/** [block] applied when [on], untouched otherwise: reads better than an if/else in a chain. */
fun Modifier.applyIf(on: Boolean, block: Modifier.() -> Modifier): Modifier =
    if (on) this.block() else this

/**
 * Gloss over a filled, accent-coloured control (a selected chip, a primary button). Clip to the
 * control's shape first, or the bloom's square corners show.
 */
fun Modifier.liquidGloss(strength: Float = 0.6f): Modifier = drawWithContent {
    drawContent()
    drawLiquidPill(strength, rim = false)
}

/**
 * A filter chip finished as glass: clear body with sheen and rim, and a gloss over the fill when it
 * is selected.
 */
fun Modifier.liquidGlassChip(shape: Shape, selected: Boolean): Modifier =
    clip(shape)
        .liquidGlassSheen()
        .applyIf(selected) { liquidGloss(0.5f) }
        .liquidGlassRim(shape)

/** A bright sheen along the top half of a thin bar, so a progress fill reads as a glass tube. */
fun Modifier.liquidProgressGloss(): Modifier = clip(RoundedCornerShape(50)).drawWithContent {
    drawContent()
    val h = size.height * 0.55f
    drawRect(
        Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = 0.45f),
            1.00f to Clear,
            startY = 0f,
            endY = h,
        ),
        size = Size(size.width, h),
    )
}

private fun Outline.toPath(): Path = Path().apply {
    when (val outline = this@toPath) {
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Generic -> addPath(outline.path)
    }
}
