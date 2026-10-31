package com.misapps.afinador.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Detector de frecuencia fundamental con el algoritmo YIN
 * (de Cheveigné & Kawahara, 2002).
 *
 * Analiza una ventana de [windowSize] muestras; la mitad sirve de ventana de
 * integración, así que el periodo más largo detectable es algo menor que
 * windowSize / 2 muestras.
 */
class PitchDetector(
    private val sampleRate: Float,
    private val windowSize: Int = 4096,
    private val threshold: Float = 0.12f,
    minFrequency: Float = 25f,
    maxFrequency: Float = 1400f,
) {
    private val half = windowSize / 2
    private val maxTau = min(half - 2, (sampleRate / minFrequency).toInt())
    private val minTau = max(2, (sampleRate / maxFrequency).toInt())
    private val cmnd = FloatArray(maxTau + 2)

    /** Frecuencia en Hz, o null si hay silencio o ningún tono claro. */
    fun detect(window: FloatArray): Float? {
        require(window.size >= windowSize) { "La ventana debe tener $windowSize muestras" }

        var energy = 0f
        for (i in 0 until windowSize) energy += window[i] * window[i]
        if (sqrt(energy / windowSize) < MIN_RMS) return null

        // Función diferencia normalizada por la media acumulada (pasos 2 y 3 de YIN).
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..maxTau + 1) {
            var sum = 0f
            for (i in 0 until half) {
                val d = window[i] - window[i + tau]
                sum += d * d
            }
            running += sum
            cmnd[tau] = if (running > 0f) sum * tau / running else 1f
        }

        // Primer valle por debajo del umbral (paso 4).
        var tau = minTau
        var found = -1
        while (tau <= maxTau) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= maxTau && cmnd[tau + 1] < cmnd[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found < 0) return null

        // Interpolación parabólica para precisión por debajo de la muestra (paso 5).
        val s0 = cmnd[found - 1]
        val s1 = cmnd[found]
        val s2 = cmnd[found + 1]
        val denom = s0 - 2 * s1 + s2
        val shift = if (denom != 0f) ((s0 - s2) / (2 * denom)).coerceIn(-1f, 1f) else 0f
        return sampleRate / (found + shift)
    }

    companion object {
        /** Por debajo de este nivel (≈ -54 dBFS) se considera silencio. */
        const val MIN_RMS = 0.002f
    }
}
