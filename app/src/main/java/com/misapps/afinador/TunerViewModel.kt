package com.misapps.afinador

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.misapps.afinador.audio.AudioEngine
import com.misapps.afinador.audio.Instrument
import com.misapps.afinador.audio.NoteMath
import com.misapps.afinador.audio.Reading
import com.misapps.afinador.audio.Tuning
import com.misapps.afinador.audio.Tunings
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class TunerUiState(
    val tuning: Tuning = Tunings.default,
    val a4: Int = 440,
    /** Cuerda elegida a mano; null = detección automática. */
    val lockedString: Int? = null,
    /** Última lectura; se mantiene (atenuada) unos instantes tras dejar de sonar. */
    val reading: Reading? = null,
    /** Hay señal ahora mismo. */
    val listening: Boolean = false,
    /** Cuerdas que se han mantenido afinadas un momento. */
    val tunedStrings: Set<Int> = emptySet(),
    val error: String? = null,
)

class TunerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("tuner", Context.MODE_PRIVATE)
    private val engine = AudioEngine(app)

    private val _state = MutableStateFlow(
        TunerUiState(
            tuning = Tunings.byId(prefs.getString(KEY_TUNING, null)) ?: Tunings.default,
            a4 = prefs.getInt(KEY_A4, 440),
        )
    )
    val state = _state.asStateFlow()

    private var running = false
    private var job: Job? = null
    private val recent = ArrayDeque<Float>()
    private var lastHeard = 0L
    private var inTuneFrames = 0

    fun start() {
        if (running) return
        running = true
        launchEngine()
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
        recent.clear()
        _state.update { it.copy(listening = false) }
    }

    fun selectInstrument(instrument: Instrument) {
        if (instrument == _state.value.tuning.instrument) return
        val tuning = Tunings.byId(prefs.getString(instrumentKey(instrument), null))
            ?: Tunings.forInstrument(instrument).first()
        selectTuning(tuning)
    }

    fun selectTuning(tuning: Tuning) {
        val previous = _state.value.tuning
        if (tuning == previous) return
        prefs.edit {
            putString(KEY_TUNING, tuning.id)
            putString(instrumentKey(tuning.instrument), tuning.id)
        }
        recent.clear()
        _state.update {
            it.copy(tuning = tuning, lockedString = null, reading = null, tunedStrings = emptySet())
        }
        if (running && tuning.decimation != previous.decimation) launchEngine()
    }

    fun toggleString(index: Int) {
        _state.update { it.copy(lockedString = if (it.lockedString == index) null else index) }
    }

    fun changeA4(delta: Int) {
        val a4 = (_state.value.a4 + delta).coerceIn(A4_MIN, A4_MAX)
        prefs.edit { putInt(KEY_A4, a4) }
        _state.update { it.copy(a4 = a4, tunedStrings = emptySet()) }
    }

    /** (Re)arranca la captura esperando a que la anterior suelte el micrófono. */
    private fun launchEngine() {
        val previous = job
        job = viewModelScope.launch {
            previous?.cancelAndJoin()
            recent.clear()
            engine.pitches(_state.value.tuning.decimation)
                .catch { e -> _state.update { it.copy(error = e.message ?: "Error de audio", listening = false) } }
                .collect(::onPitch)
        }
    }

    private fun onPitch(frequency: Float?) {
        val now = SystemClock.elapsedRealtime()
        if (frequency == null) {
            if (now - lastHeard > RESET_MS) recent.clear()
            inTuneFrames = 0
            if (_state.value.listening && now - lastHeard > HOLD_MS) {
                _state.update { it.copy(listening = false) }
            }
            return
        }
        lastHeard = now

        // Mediana de las últimas lecturas: descarta saltos sueltos (ataque, armónicos).
        recent.addLast(frequency)
        if (recent.size > MEDIAN_SIZE) recent.removeFirst()
        val smoothed = recent.sorted()[recent.size / 2]

        _state.update { s ->
            val reading = NoteMath.analyze(smoothed, s.tuning, s.a4.toFloat(), s.lockedString)
            val index = reading.stringIndex
            inTuneFrames = if (abs(reading.cents) <= IN_TUNE_CENTS) inTuneFrames + 1 else 0
            val tuned = when {
                index == null -> s.tunedStrings
                inTuneFrames >= TUNED_FRAMES -> s.tunedStrings + index
                abs(reading.cents) > OUT_OF_TUNE_CENTS -> s.tunedStrings - index
                else -> s.tunedStrings
            }
            s.copy(reading = reading, listening = true, tunedStrings = tuned, error = null)
        }
    }

    private fun instrumentKey(instrument: Instrument) = "tuning_${instrument.name}"

    companion object {
        const val IN_TUNE_CENTS = 3f
        private const val OUT_OF_TUNE_CENTS = 10f
        private const val TUNED_FRAMES = 10 // ≈ medio segundo
        private const val MEDIAN_SIZE = 5
        private const val HOLD_MS = 600L
        private const val RESET_MS = 300L
        const val A4_MIN = 415
        const val A4_MAX = 466
        private const val KEY_TUNING = "tuning"
        private const val KEY_A4 = "a4"
    }
}
