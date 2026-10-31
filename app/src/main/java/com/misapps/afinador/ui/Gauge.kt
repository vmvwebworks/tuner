package com.misapps.afinador.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Grados de giro de la aguja para ±50 cents. */
private const val SWEEP = 60f

/**
 * Medidor de aguja de -50 a +50 cents. [cents] null deja la aguja en el
 * centro; [active] = false la atenúa (sin señal).
 */
@Composable
fun Gauge(cents: Float?, active: Boolean, modifier: Modifier = Modifier) {
    val target = (cents ?: 0f).coerceIn(-50f, 50f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    val needleColor by animateColorAsState(
        targetValue = if (cents == null || !active) Faint else centsColor(cents),
        label = "needleColor",
    )
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Muted, fontSize = 12.sp)

    Canvas(modifier) {
        val radius = min(size.width / 2f, size.height) * 0.92f
        val center = Offset(size.width / 2f, size.height * 0.96f)

        // Zona afinada (±3 cents) y arco base.
        drawArcCents(center, radius, -50f, 50f, Faint, 3f)
        drawArcCents(center, radius, -3f, 3f, InTune.copy(alpha = 0.9f), 10f)

        for (c in -50..50 step 5) {
            val major = c % 25 == 0
            val length = if (major) 22f else if (c % 10 == 0) 14f else 8f
            val angle = angleFor(c.toFloat())
            drawLine(
                color = if (c == 0) OnSurface else if (major) Muted else Faint,
                start = polar(center, radius - 8f, angle),
                end = polar(center, radius - 8f - length, angle),
                strokeWidth = if (major) 3f else 2f,
                cap = StrokeCap.Round,
            )
            if (major) {
                val text = when {
                    c > 0 -> "+$c"
                    else -> "$c"
                }
                val layout = measurer.measure(text, labelStyle)
                val pos = polar(center, radius - 8f - length - 22f, angle)
                drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
            }
        }

        val angle = angleFor(animated)
        drawLine(
            color = needleColor,
            start = center,
            end = polar(center, radius - 4f, angle),
            strokeWidth = 7f,
            cap = StrokeCap.Round,
        )
        drawCircle(needleColor, radius = 14f, center = center)
        drawCircle(Background, radius = 6f, center = center)
    }
}

private fun angleFor(cents: Float) = cents / 50f * SWEEP

/** Punto a [r] del centro, con el ángulo medido en grados desde la vertical. */
private fun polar(center: Offset, r: Float, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    return Offset(center.x + r * sin(rad).toFloat(), center.y - r * cos(rad).toFloat())
}

private fun DrawScope.drawArcCents(
    center: Offset, radius: Float, from: Float, to: Float,
    color: androidx.compose.ui.graphics.Color, width: Float,
) {
    // drawArc mide desde el eje X positivo en sentido horario; la vertical es -90°.
    val start = -90f + angleFor(from)
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = angleFor(to) - angleFor(from),
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
}
