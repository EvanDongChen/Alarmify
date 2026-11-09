package com.evanchen.alarmify.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate

/**
 * A minimal fish: an almond body, a forked tail and one eye, facing along [angle] (degrees, 0 = right).
 * [sway] (-1..1) flicks the tail. [filled] draws it solid with the eye knocked out in [eyeColor].
 */
fun DrawScope.drawFish(
    center: Offset,
    length: Float,
    angle: Float,
    color: Color,
    sway: Float = 0f,
    filled: Boolean = true,
    eyeColor: Color = Night.Bg,
    strokeWidth: Float = length * 0.06f,
) {
    val l = length
    val h = l * 0.42f
    val s = sway * h * 0.35f
    val body = Path().apply {
        moveTo(l * 0.5f, 0f)
        quadraticTo(l * 0.12f, -h * 0.95f, -l * 0.26f, s * 0.3f)
        quadraticTo(l * 0.12f, h * 0.95f, l * 0.5f, 0f)
        close()
    }
    val tail = Path().apply {
        moveTo(-l * 0.22f, s * 0.3f)
        lineTo(-l * 0.5f, -h * 0.48f + s)
        lineTo(-l * 0.42f, s)
        lineTo(-l * 0.5f, h * 0.48f + s)
        close()
    }
    translate(center.x, center.y) {
        rotate(angle, pivot = Offset.Zero) {
            if (filled) {
                drawPath(body, color)
                drawPath(tail, color)
                drawCircle(eyeColor, radius = l * 0.045f, center = Offset(l * 0.27f, -h * 0.1f))
            } else {
                val st = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(body, color, style = st)
                drawPath(tail, color, style = st)
                drawCircle(color, radius = l * 0.04f, center = Offset(l * 0.27f, -h * 0.1f))
            }
        }
    }
}

/** A small outlined fish that idly flicks its tail, for headers and empty states. */
@Composable
fun FishGlyph(modifier: Modifier, color: Color = Night.FgSoft, facingRight: Boolean = true) {
    val t = rememberInfiniteTransition(label = "fishGlyph")
    val sway by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "sway")
    Canvas(modifier) {
        drawFish(
            center = center,
            length = size.width * 0.92f,
            angle = if (facingRight) 0f else 180f,
            color = color,
            sway = sway,
            filled = false,
            strokeWidth = 1.5f * density,
        )
    }
}
