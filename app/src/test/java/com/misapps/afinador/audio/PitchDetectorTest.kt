package com.misapps.afinador.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class PitchDetectorTest {

    private val window = 4096

    /** Cuerda pulsada aproximada: fundamental + armónicos decrecientes. */
    private fun pluck(freq: Double, rate: Double, harmonics: DoubleArray = doubleArrayOf(1.0, 0.6, 0.4, 0.25, 0.15)) =
        FloatArray(window) { i ->
            val t = i / rate
            var s = 0.0
            harmonics.forEachIndexed { h, amp -> s += amp * sin(2 * PI * freq * (h + 1) * t) }
            (0.2 * s * exp(-t)).toFloat()
        }

    private fun assertWithinCents(expected: Double, actual: Float?, maxCents: Double) {
        assertNotNull("No detectó $expected Hz", actual)
        val cents = NoteMath.cents(actual!!, expected.toFloat())
        assertTrue("Esperado $expected Hz, detectado $actual Hz ($cents ¢)", abs(cents) <= maxCents)
    }

    @Test
    fun detectsGuitarStrings() {
        val detector = PitchDetector(44100f, window)
        for (freq in listOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)) {
            assertWithinCents(freq, detector.detect(pluck(freq, 44100.0)), 1.0)
        }
    }

    @Test
    fun detectsBassStringsWithDecimation() {
        val detector = PitchDetector(22050f, window)
        for (freq in listOf(30.87, 41.2, 55.0, 73.42, 98.0)) {
            assertWithinCents(freq, detector.detect(pluck(freq, 22050.0)), 1.0)
        }
    }

    @Test
    fun weakFundamentalIsNotReportedAnOctaveUp() {
        val detector = PitchDetector(44100f, window)
        val signal = pluck(82.41, 44100.0, doubleArrayOf(0.3, 1.0, 0.7, 0.4))
        assertWithinCents(82.41, detector.detect(signal), 1.0)
    }

    @Test
    fun detectsSlightlyDetunedNote() {
        val detector = PitchDetector(44100f, window)
        val freq = 110.0 * Math.pow(2.0, 7 / 1200.0) // +7 cents
        val detected = detector.detect(pluck(freq, 44100.0))
        assertEquals(7.0, NoteMath.cents(detected!!, 110f).toDouble(), 1.0)
    }

    @Test
    fun silenceAndNoiseGiveNoPitch() {
        val detector = PitchDetector(44100f, window)
        assertNull(detector.detect(FloatArray(window)))
        val random = Random(42)
        val noise = FloatArray(window) { (random.nextFloat() - 0.5f) * 0.2f }
        assertNull(detector.detect(noise))
    }
}
