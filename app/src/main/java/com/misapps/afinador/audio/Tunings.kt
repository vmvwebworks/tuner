package com.misapps.afinador.audio

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Instrument(val label: String) {
    GUITAR("Guitarra"),
    BASS("Bajo"),
    CHROMATIC("Cromático"),
}

/**
 * Afinación: cuerdas como números MIDI, de la más grave a la más aguda
 * (E2 = 40, A4 = 69). Sin cuerdas = modo cromático.
 */
data class Tuning(
    val id: String,
    val instrument: Instrument,
    val name: String,
    val strings: List<Int>,
    val flats: Boolean = false,
) {
    /**
     * Las cuerdas muy graves necesitan más tiempo de análisis: se diezma la
     * señal ×2 para que la misma ventana abarque el doble de periodos.
     */
    val decimation: Int get() = if (strings.isNotEmpty() && strings.min() <= 33) 2 else 1
}

object Tunings {
    val all = listOf(
        Tuning("guitar_standard", Instrument.GUITAR, "Estándar (E A D G B E)", listOf(40, 45, 50, 55, 59, 64)),
        Tuning("guitar_drop_d", Instrument.GUITAR, "Drop D (D A D G B E)", listOf(38, 45, 50, 55, 59, 64)),
        Tuning("guitar_half_down", Instrument.GUITAR, "Medio tono abajo (E♭)", listOf(39, 44, 49, 54, 58, 63), flats = true),
        Tuning("guitar_d_standard", Instrument.GUITAR, "Un tono abajo (D G C F A D)", listOf(38, 43, 48, 53, 57, 62)),
        Tuning("guitar_drop_c", Instrument.GUITAR, "Drop C (C G C F A D)", listOf(36, 43, 48, 53, 57, 62)),
        Tuning("guitar_open_g", Instrument.GUITAR, "Open G (D G D G B D)", listOf(38, 43, 50, 55, 59, 62)),
        Tuning("guitar_dadgad", Instrument.GUITAR, "DADGAD", listOf(38, 45, 50, 55, 57, 62)),
        Tuning("guitar_7_standard", Instrument.GUITAR, "7 cuerdas (B E A D G B E)", listOf(35, 40, 45, 50, 55, 59, 64)),
        Tuning("bass_4_standard", Instrument.BASS, "4 cuerdas (E A D G)", listOf(28, 33, 38, 43)),
        Tuning("bass_4_drop_d", Instrument.BASS, "4 cuerdas Drop D (D A D G)", listOf(26, 33, 38, 43)),
        Tuning("bass_4_half_down", Instrument.BASS, "4 cuerdas medio tono abajo (E♭)", listOf(27, 32, 37, 42), flats = true),
        Tuning("bass_5_standard", Instrument.BASS, "5 cuerdas (B E A D G)", listOf(23, 28, 33, 38, 43)),
        Tuning("bass_6_standard", Instrument.BASS, "6 cuerdas (B E A D G C)", listOf(23, 28, 33, 38, 43, 48)),
        Tuning("chromatic", Instrument.CHROMATIC, "Cromático", emptyList()),
    )

    val default = all.first()

    fun byId(id: String?): Tuning? = all.firstOrNull { it.id == id }

    fun forInstrument(instrument: Instrument) = all.filter { it.instrument == instrument }
}

/** Resultado de comparar una frecuencia con la nota objetivo. */
data class Reading(
    /** Nota objetivo (MIDI). */
    val midi: Int,
    val frequency: Float,
    /** Desviación respecto a la nota objetivo: negativo = grave, positivo = agudo. */
    val cents: Float,
    /** Índice de la cuerda objetivo, o null en modo cromático. */
    val stringIndex: Int?,
)

object NoteMath {
    private val SHARPS = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val FLATS = arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")

    fun frequency(midi: Int, a4: Float): Float = (a4 * 2.0.pow((midi - 69) / 12.0)).toFloat()

    fun nearestMidi(frequency: Float, a4: Float): Int = (69 + 12 * log2(frequency / a4)).roundToInt()

    fun cents(frequency: Float, reference: Float): Float = 1200 * log2(frequency / reference)

    fun name(midi: Int, flats: Boolean = false): String = (if (flats) FLATS else SHARPS)[midi.mod(12)]

    fun octave(midi: Int): Int = midi.floorDiv(12) - 1

    /**
     * Compara [frequency] con la afinación. Sin cuerda fijada se elige la
     * cuerda más cercana; con [lockedString] se compara siempre con esa y se
     * pliegan los errores de octava (armónicos) hacia ella.
     */
    fun analyze(frequency: Float, tuning: Tuning, a4: Float, lockedString: Int? = null): Reading {
        if (tuning.strings.isEmpty()) {
            val midi = nearestMidi(frequency, a4)
            return Reading(midi, frequency, cents(frequency, frequency(midi, a4)), null)
        }
        val index = lockedString?.takeIf { it in tuning.strings.indices }
            ?: tuning.strings.indices.minBy { abs(cents(frequency, frequency(tuning.strings[it], a4))) }
        val midi = tuning.strings[index]
        var c = cents(frequency, frequency(midi, a4))
        if (lockedString != null) {
            while (c > 600) c -= 1200
            while (c < -600) c += 1200
        }
        return Reading(midi, frequency, c, index)
    }
}
