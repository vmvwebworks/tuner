# Afinador

Afinador para Android. Escucha por el micrófono, detecta la nota y marca con una aguja cuántos cents te faltan para la cuerda que toca.

Kotlin + Jetpack Compose.

## Qué hay

- **Afinaciones**: guitarra (estándar, Drop D, medio tono y un tono abajo, Drop C, Open G, DADGAD, 7 cuerdas), bajo de 4, 5 y 6 cuerdas, y modo cromático.
- **Cuerdas**: reconoce sola la cuerda que suena o se puede fijar una como objetivo. Cada cuerda queda marcada como afinada tras medio segundo dentro de ±3 cents.
- **Calibración**: La4 de 415 a 466 Hz. Se recuerdan la afinación de cada instrumento y la calibración.

## Cómo está hecho

- `PitchDetector`: algoritmo YIN (de Cheveigné y Kawahara, 2002), de 25 Hz a 1,4 kHz. Lo cubren los tests.
- `AudioEngine`: `AudioRecord` a 44,1 kHz con ventanas de 4096 muestras. Usa la entrada sin procesar (sin control de ganancia ni supresión de ruido) si el móvil la admite. En cuerdas muy graves diezma la señal ×2 para que la ventana abarque más periodos.
- `TunerViewModel`: mediana de las últimas cinco lecturas para descartar saltos del ataque y de los armónicos.
- `Tunings` / `NoteMath`: afinaciones como números MIDI y conversión entre frecuencia, nota y cents.

Requiere Android 9 (API 28) o superior.
