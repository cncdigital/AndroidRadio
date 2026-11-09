# Fondos reactivos del reproductor · 0.10.29 / código 39

Esta candidata corrige los fondos decorativos de 0.10.28: ahora responden al PCM que reproduce la app. No hay APK publicado con este cambio todavía.

## Uso

- Desliza horizontalmente la portada del teléfono, normal o maximizada, para recorrer Portada original, Barras neón, Ondas, Túnel, Aurora y Estrellas. El botón «Fondo» permite elegir directamente y guarda la preferencia.
- La letra conserva su capa superior. En pantalla completa, arrastrar la letra o «Mover letra» sigue moviéndola, sin cambiar el fondo. El gesto de fondo funciona en el espacio libre de la portada.
- Barras muestra 28 bandas de frecuencia reales. Ondas, Túnel, Aurora y Estrellas cambian su amplitud, tamaño, brillo y velocidad con la energía, graves y agudos de la música. El reloj no inventa pulsos: en silencio la fase se detiene.
- El fondo informa «Esperando audio» cuando no hay datos PCM válidos, «Sin señal de audio» durante silencio, «En pausa», «Ahorro de batería · fondo estático» o «Movimiento reducido · fondo estático» según corresponda. No cambia ajustes del sistema.
- La animación y el análisis se detienen al pausar, minimizar, ocultar ambas vistas, seleccionar Portada original, activar ahorro de batería o desactivar animaciones de Android. Los cambios de ahorro y accesibilidad se detectan mientras la vista está adjunta.

## Audio, privacidad y sincronización

- `RadioAnalysisAudioProcessor` está integrado en el `DefaultAudioSink` de la fábrica de música, tanto en reproducción normal como con karaoke. Analiza después del karaoke sin cambiar ningún byte del PCM que recibe. Los efectos siguen apagados por defecto y funcionan aparte.
- No usa `android.media.audiofx.Visualizer`, micrófono, captura de la sesión global, `RECORD_AUDIO`, red ni archivos de audio nuevos. Sólo mantiene una ventana de trabajo transitoria (512–8192 muestras por canal) y hasta 96 resultados compactos de análisis. Los datos no se guardan ni se envían.
- El análisis usa FFT estéreo con ventana Hann y combina potencia de ambos canales, sin cancelar música estéreo en antifase. Admite PCM de 16 bits mono/estéreo a 8–192 kHz. Media3 convierte normalmente la salida de los decodificadores al formato de la cadena. Otros formatos muestran el estado de espera en vez de fingir un espectro.
- La FFT sólo se calcula si una vista visible necesita movimiento. Los búferes de análisis se preparan al configurar el formato; el procesamiento de muestras y FFT no crea objetos por muestra o ventana. Las copias pequeñas de características se comparten bajo un bloqueo breve, nunca la FFT ni las muestras.
- La cadena solicita decodificación PCM, no salida comprimida directa/offload, porque ésta omitiría el análisis. Esto puede consumir más energía que el offload de hardware, incluso cuando se muestra la portada; el análisis FFT sí se desactiva en ese caso.
- Los resultados se seleccionan por la posición real de reproducción del AudioSink, no por el último audio decodificado adelantado. Las ventanas duran aproximadamente 43–64 ms y la vista dibuja como máximo a 24 fps. Un reloj sin actualizar durante 250 ms o características con más de 150 ms se descartan. El inicio de una fuente, la reanudación o un cambio de ruta pueden mostrar brevemente espera mientras llega audio alineado.
- Esto no garantiza sincronización de muestra exacta ni compensa una latencia Bluetooth externa que Android no reporte. El punto de análisis está antes del volumen, ducking y efectos del dispositivo: bajar volumen no cambia el espectro interno. Los anuncios de voz usan otro reproductor; los comerciales de la cola no publican un falso espectro de la canción.
- Las pausas, discontinuidades, cambios de formato/pista y liberación invalidan los resultados. La caché, anuncios, mezcla de voz, letra, controles de transporte y Android Auto conservan su lógica.

## Verificación antes de entregar APK

Requiere JDK 17, Android SDK 36/build-tools 36.0.0 y Gradle 8.14.3.

1. `gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon`.
2. En emulador/teléfono: `gradle connectedDebugAndroidTest --no-daemon`.
3. Las pruebas unitarias cubren igualdad byte a byte, energía/pico, silencio, bandas de graves/medios/agudos, antifase, mono, tasas bajas/altas, entrada fragmentada, demanda desactivada, antigüedad y búfer acotado.
4. La prueba instrumentada `productionMusicSinkPublishesRealPcmFeaturesAndClearsOnPause` reproduce un WAV local con la fábrica de producción normal y karaoke, verifica niveles PCM reales, pausa/reanudación, exclusión de comerciales y liberación. Compilarla no equivale a ejecutarla.
5. Las pruebas de dibujo cubren respuesta a energía de los cinco estilos, barras por frecuencia, fase inmóvil en silencio y vista oculta/desconectada. También se conservan pruebas de selección, gestos, capas de letra, preferencias, efectos y reproducción.
6. En teléfono real: comparar música con golpes y silencio, ambos sentidos de cambio de fondo, maximizar/cerrar repetidamente, mover letra, buscar otra posición, cambiar canción, pausar/reanudar y reabrir. Probar altavoz y Bluetooth, ahorro de batería, animaciones desactivadas, rotación y pantalla bloqueada. Confirmar que no aparece un falso pulso en pausa/silencio.
7. Escuchar karaoke, anuncios y ducking, y comprobar Android Auto en su interfaz estándar. Las pruebas automáticas no certifican la percepción de sincronía Bluetooth ni la audición en hardware real.

Antes de publicar, usar la firma permanente y verificarla. El workflow `build-apk.yml` publica al hacer push a `main`; no usar esa rama sólo para probar esta candidata. Consultar BUILD-VERIFICATION.md para los resultados realmente ejecutados.
