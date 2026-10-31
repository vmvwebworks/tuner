package com.misapps.afinador.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF101114)
val Surface = Color(0xFF1B1D22)
val SurfaceHigh = Color(0xFF262931)
val OnSurface = Color(0xFFE8E8EA)
val Muted = Color(0xFF8A8D96)
val Faint = Color(0xFF3A3D45)
val InTune = Color(0xFF3DDC84)
val Close = Color(0xFFFFB547)
val Off = Color(0xFFFF5A5F)

/** Color según lo lejos que esté de la nota. */
fun centsColor(cents: Float): Color {
    val c = kotlin.math.abs(cents)
    return when {
        c <= 3f -> InTune
        c <= 15f -> Close
        else -> Off
    }
}

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = InTune,
            onPrimary = Background,
            secondary = Close,
            background = Background,
            onBackground = OnSurface,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceHigh,
            onSurfaceVariant = Muted,
            surfaceContainer = Surface,
            outline = Faint,
            secondaryContainer = SurfaceHigh,
            onSecondaryContainer = OnSurface,
        ),
        content = content,
    )
}
