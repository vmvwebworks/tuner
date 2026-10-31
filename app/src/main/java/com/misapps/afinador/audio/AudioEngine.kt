package com.misapps.afinador.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.max

/** Lee el micrófono y emite la frecuencia detectada (o null) unas 20 veces por segundo. */
class AudioEngine(private val context: Context) {

    @SuppressLint("MissingPermission") // Se comprueba justo antes de abrir el micrófono.
    fun pitches(decimation: Int): Flow<Float?> = flow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) throw SecurityException("Falta el permiso de micrófono")

        // Ventana de análisis con solape del 50 %: cada lectura aporta `hop` muestras nuevas.
        val hop = WINDOW / 2 / decimation
        val raw = ShortArray(hop * decimation)
        val window = FloatArray(WINDOW)
        val detector = PitchDetector(SAMPLE_RATE.toFloat() / decimation, WINDOW)

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            audioSource(), SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, max(minBuffer, raw.size * 2 * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("No se pudo abrir el micrófono")
        }

        try {
            record.startRecording()
            while (true) {
                var read = 0
                while (read < raw.size) {
                    val n = record.read(raw, read, raw.size - read)
                    if (n < 0) error("Error leyendo el micrófono ($n)")
                    read += n
                }
                System.arraycopy(window, hop, window, 0, WINDOW - hop)
                val base = WINDOW - hop
                for (i in 0 until hop) {
                    // Media de cada grupo: filtro paso bajo mínimo antes de diezmar.
                    var acc = 0f
                    for (j in 0 until decimation) acc += raw[i * decimation + j]
                    window[base + i] = acc / decimation / Short.MAX_VALUE
                }
                emit(detector.detect(window))
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    /** Sin procesado (sin control de ganancia ni supresión de ruido) si el móvil lo admite. */
    private fun audioSource(): Int {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val unprocessed = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val WINDOW = 4096
    }
}
