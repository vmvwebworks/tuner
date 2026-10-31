package com.misapps.afinador.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.misapps.afinador.TunerUiState
import com.misapps.afinador.TunerViewModel
import com.misapps.afinador.audio.Instrument
import com.misapps.afinador.audio.NoteMath
import com.misapps.afinador.audio.Reading
import com.misapps.afinador.audio.Tuning
import com.misapps.afinador.audio.Tunings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TunerApp(vm: TunerViewModel = viewModel()) {
    val context = LocalContext.current
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasPermission()) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    // Al volver de Ajustes el permiso puede haber cambiado.
    LifecycleResumeEffect(Unit) {
        granted = hasPermission()
        if (!granted && !asked) launcher.launch(Manifest.permission.RECORD_AUDIO)
        onPauseOrDispose { }
    }

    if (granted) {
        LifecycleStartEffect(Unit) {
            vm.start()
            onStopOrDispose { vm.stop() }
        }
        val state by vm.state.collectAsStateWithLifecycle()
        TunerScreen(
            state = state,
            onInstrument = vm::selectInstrument,
            onTuning = vm::selectTuning,
            onString = vm::toggleString,
            onA4 = vm::changeA4,
        )
    } else {
        PermissionScreen(
            onRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
            onSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            },
            showSettings = asked,
        )
    }
}

@Composable
fun TunerScreen(
    state: TunerUiState,
    onInstrument: (Instrument) -> Unit,
    onTuning: (Tuning) -> Unit,
    onString: (Int) -> Unit,
    onA4: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InstrumentSelector(state.tuning.instrument, onInstrument)
        Spacer(Modifier.height(12.dp))
        TuningSelector(state.tuning, onTuning)

        Spacer(Modifier.weight(1f))

        val reading = state.reading
        Gauge(
            cents = reading?.cents,
            active = state.listening,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.9f),
        )
        Spacer(Modifier.height(8.dp))
        NoteDisplay(reading, state.tuning.flats, state.listening)
        Spacer(Modifier.height(4.dp))
        StatusLine(state)

        Spacer(Modifier.weight(1f))

        if (state.tuning.strings.isNotEmpty()) {
            StringRow(state, onString)
            Spacer(Modifier.height(20.dp))
        }
        CalibrationRow(state.a4, onA4)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstrumentSelector(selected: Instrument, onSelect: (Instrument) -> Unit) {
    val options = Instrument.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, instrument ->
            SegmentedButton(
                selected = instrument == selected,
                onClick = { onSelect(instrument) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
            ) { Text(instrument.label) }
        }
    }
}

@Composable
private fun TuningSelector(tuning: Tuning, onSelect: (Tuning) -> Unit) {
    val options = Tunings.forInstrument(tuning.instrument)
    if (options.size <= 1) {
        Text(
            "Detecta cualquier nota",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(tuning.name, color = OnSurface)
            Text("  ▾", color = Muted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.name,
                            color = if (option == tuning) InTune else OnSurface,
                            fontWeight = if (option == tuning) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun NoteDisplay(reading: Reading?, flats: Boolean, active: Boolean) {
    val color by animateColorAsState(
        targetValue = when {
            reading == null -> Faint
            !active -> Muted
            else -> centsColor(reading.cents)
        },
        label = "noteColor",
    )
    val name = reading?.let { NoteMath.name(it.midi, flats) } ?: "–"
    val text = buildAnnotatedString {
        append(name.take(1))
        if (name.length > 1) {
            withStyle(SpanStyle(fontSize = 48.sp, baselineShift = BaselineShift(0.9f))) { append(name.drop(1)) }
        }
        if (reading != null) {
            withStyle(SpanStyle(fontSize = 32.sp, color = Muted, baselineShift = BaselineShift(-0.1f))) {
                append(NoteMath.octave(reading.midi).toString())
            }
        }
    }
    Text(text, fontSize = 104.sp, fontWeight = FontWeight.Bold, color = color, lineHeight = 110.sp)

    Row(
        Modifier.alpha(if (reading != null && active) 1f else 0.5f),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            reading?.let { String.format(Locale.US, "%.1f Hz", it.frequency) } ?: "— Hz",
            color = Muted,
            fontSize = 16.sp,
        )
        Text(
            reading?.let {
                val c = it.cents.roundToInt()
                if (c > 0) "+$c ¢" else "$c ¢"
            } ?: "— ¢",
            color = Muted,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun StatusLine(state: TunerUiState) {
    val reading = state.reading
    val (text, color) = when {
        state.error != null -> state.error to Off
        reading == null || !state.listening -> "Toca una cuerda" to Muted
        abs(reading.cents) <= TunerViewModel.IN_TUNE_CENTS -> "¡Afinada!" to InTune
        reading.stringIndex == null && reading.cents < 0 -> "Grave" to centsColor(reading.cents)
        reading.stringIndex == null -> "Aguda" to centsColor(reading.cents)
        reading.cents < 0 -> "Grave · tensa la cuerda" to centsColor(reading.cents)
        else -> "Aguda · afloja la cuerda" to centsColor(reading.cents)
    }
    Text(
        text,
        color = color,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun StringRow(state: TunerUiState, onString: (Int) -> Unit) {
    val strings = state.tuning.strings
    Text(
        if (state.lockedString == null) "Automático · toca una cuerda para fijarla"
        else "Cuerda fijada · tócala otra vez para volver a automático",
        color = Muted,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        strings.forEachIndexed { i, midi ->
            val locked = state.lockedString == i
            val target = state.listening && state.reading?.stringIndex == i
            val tuned = i in state.tunedStrings
            val ring = when {
                target -> centsColor(state.reading!!.cents)
                locked -> OnSurface
                else -> Color.Transparent
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(if (strings.size > 6) 42.dp else 48.dp)
                        .clip(CircleShape)
                        .background(if (locked) SurfaceHigh else Surface)
                        .border(2.dp, ring, CircleShape)
                        .clickable { onString(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        NoteMath.name(midi, state.tuning.flats),
                        color = if (tuned) InTune else OnSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (tuned) "✓" else "${strings.size - i}",
                    color = if (tuned) InTune else Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun CalibrationRow(a4: Int, onA4: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Calibración", color = Muted, fontSize = 12.sp)
            Text("La4 = $a4 Hz", color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        if (a4 != 440) {
            TextButton(onClick = { onA4(440 - a4) }) { Text("440") }
        }
        FilledTonalIconButton(onClick = { onA4(-1) }, enabled = a4 > TunerViewModel.A4_MIN) {
            Text("−", fontSize = 20.sp)
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = { onA4(1) }, enabled = a4 < TunerViewModel.A4_MAX) {
            Text("+", fontSize = 20.sp)
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onSettings: () -> Unit, showSettings: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎸", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Para afinar necesito escuchar tu instrumento",
            color = OnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "El audio se analiza en el móvil y no se graba ni se envía a ningún sitio.",
            color = Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Permitir micrófono") }
        if (showSettings) {
            TextButton(onClick = onSettings) { Text("Abrir ajustes de la app") }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101114, heightDp = 780)
@Composable
private fun TunerScreenPreview() {
    TunerTheme {
        TunerScreen(
            state = TunerUiState(
                reading = Reading(45, 109.2f, -12.6f, 1),
                listening = true,
                tunedStrings = setOf(0),
            ),
            onInstrument = {}, onTuning = {}, onString = {}, onA4 = {},
        )
    }
}
