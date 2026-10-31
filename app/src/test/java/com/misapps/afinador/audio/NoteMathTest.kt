package com.misapps.afinador.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMathTest {

    private val standard = Tunings.byId("guitar_standard")!!

    @Test
    fun noteFrequencies() {
        assertEquals(440f, NoteMath.frequency(69, 440f), 0.001f)
        assertEquals(82.407f, NoteMath.frequency(40, 440f), 0.001f)
        assertEquals(41.203f, NoteMath.frequency(28, 440f), 0.001f)
        assertEquals(432f, NoteMath.frequency(69, 432f), 0.001f)
    }

    @Test
    fun noteNames() {
        assertEquals("E", NoteMath.name(40))
        assertEquals(2, NoteMath.octave(40))
        assertEquals("A♯", NoteMath.name(70))
        assertEquals("B♭", NoteMath.name(70, flats = true))
        assertEquals(0, NoteMath.octave(23))
    }

    @Test
    fun autoPicksNearestString() {
        val reading = NoteMath.analyze(108f, standard, 440f)
        assertEquals(1, reading.stringIndex)
        assertEquals(45, reading.midi)
        assertTrue(reading.cents < 0)
    }

    @Test
    fun lockedStringFoldsOctaveErrors() {
        // Armónico de la 6ª (E3) con la 6ª fijada: se compara con E2 sin el salto de octava.
        val reading = NoteMath.analyze(165f, standard, 440f, lockedString = 0)
        assertEquals(0, reading.stringIndex)
        assertEquals(1.5f, reading.cents, 1f)
    }

    @Test
    fun chromaticFindsNearestNote() {
        val reading = NoteMath.analyze(445f, Tunings.byId("chromatic")!!, 440f)
        assertEquals(69, reading.midi)
        assertEquals(19.56f, reading.cents, 0.1f)
    }
}
