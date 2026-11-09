package com.evanchen.alarmify.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.LatLon
import com.evanchen.alarmify.data.Place
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * A sonar view of a place alarm: the destination is a coral buoy in the middle, the dashed circle is how close
 * you need to get, and the fish is you, at your real bearing (north up) and scaled distance, nosing towards it.
 */
@Composable
fun Sonar(
    place: Place,
    radiusM: Int,
    here: LatLon?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val clock = rememberAmbientTime()
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        val time by clock
        val c = center
        val r = size.minDimension / 2f - (if (compact) 2.dp.toPx() else 14.dp.toPx())

        // Where you are, in metres east/north of the destination.
        val offset = here?.let {
            val east = (it.lon - place.lon) * 111_320.0 * cos(Math.toRadians(place.lat))
            val north = (it.lat - place.lat) * 110_540.0
            Offset(east.toFloat(), north.toFloat())
        }
        val dist = offset?.let { hypot(it.x, it.y) } ?: 0f
        // Metres represented by the outer ring: enough to show both the circle and you.
        val span = max(radiusM * 1.7f, dist * 1.15f)
        val pxPerM = r / span

        // Rings.
        for (k in 1..4) {
            drawCircle(Night.Rule, r * k / 4f, c, style = Stroke(1.dp.toPx()))
        }
        // The sweep.
        val angle = (time * 70f) % 360f
        rotate(angle, c) {
            drawArc(
                Brush.sweepGradient(0f to Color.Transparent, 0.16f to Night.Fg.copy(alpha = 0.10f), 0.161f to Color.Transparent, center = c),
                startAngle = 0f, sweepAngle = 58f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )
        }
        // The circle you need to reach.
        val rr = (radiusM * pxPerM).coerceAtLeast(6.dp.toPx())
        drawCircle(Night.Accent.copy(alpha = 0.08f), rr, c)
        drawCircle(
            Night.Accent.copy(alpha = 0.75f), rr, c,
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
        )
        // The buoy.
        val pulse = (time % 2.4f) / 2.4f
        drawCircle(Night.Accent.copy(alpha = (1f - pulse) * 0.35f), (if (compact) 4.dp else 6.dp).toPx() + pulse * 14.dp.toPx(), c)
        drawCircle(Night.Accent, (if (compact) 3.5.dp else 5.5.dp).toPx(), c)

        // You.
        if (offset != null) {
            val p = c + Offset(offset.x * pxPerM, -offset.y * pxPerM)
            val towards = Math.toDegrees(atan2((c.y - p.y).toDouble(), (c.x - p.x).toDouble())).toFloat()
            val len = (if (compact) 14.dp else 24.dp).toPx()
            drawCircle(Night.Bg.copy(alpha = 0.7f), len * 0.55f, p)
            drawFish(p, len, if (dist < 1f) 0f else towards, Night.Fg, sway = kotlin.math.sin(time * 6f) * 0.6f)
        }

        if (!compact) {
            val n = measurer.measure("N", TextStyle(fontFamily = Sans, fontSize = 10.sp, color = Night.FgFaint, letterSpacing = 1.sp))
            drawText(n, topLeft = Offset(c.x - n.size.width / 2f, c.y - r - n.size.height - 1.dp.toPx()))
        }
    }
}
