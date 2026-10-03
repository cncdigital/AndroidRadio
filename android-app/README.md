# Radio Sindical para Android y Android Auto

La app abre directamente el reproductor nativo, sin WebView ni inicio de sesión. Android Auto muestra canciones activas mediante `MediaLibraryService`, con una cola distinta al iniciar, datos de DeVi cada cinco canciones terminadas, presentación de cada canción y comerciales al alcanzar el intervalo administrado al terminar una canción. El catálogo se actualiza cada minuto sin cortar el tema actual. Las rutas públicas de escucha sólo sirven canciones y comerciales activos; la administración y el catálogo privado del portal siguen protegidos. Los avisos de micrófono en vivo requieren una integración de audio específica y todavía no se escuchan desde esta app.

Las portadas se entregan a Android Auto mediante `RadioArtworkProvider`, que descarga y conserva brevemente las imágenes activas; el automóvil muestra el arte en su reproductor de sistema. En el teléfono, la línea de letra con marca de tiempo aparece ampliada sobre la portada y en el panel de karaoke. Por seguridad vial, no se coloca la letra en la pantalla de Android Auto.

La pantalla del **teléfono** muestra la letra cargada y sigue las líneas que tengan marcas de tiempo. Android Auto mantiene su pantalla de reproducción estándar con título, artista y controles; no incorpora letras desplazables en la pantalla del vehículo. DeVi presenta por voz cada canción, usando el título y el artista conocidos. La voz nativa usa el motor de texto a voz instalado en el dispositivo.

El teléfono tiene el botón **Maximizar portada**. Abre una vista de pantalla completa con la portada, la línea sincronizada, controles de reproducción y un botón para volver; mantiene la pantalla encendida sólo mientras está abierta. Desde la versión 0.9.0, los controles reservan el espacio de navegación del teléfono para que no queden ocultos. Android Auto utiliza la interfaz de reproducción del automóvil y no admite este diálogo propio en la pantalla del vehículo.

Desde 0.10.1, DeVi utiliza locuciones MP3 de voz natural del portal para presentar las canciones y los datos oficiales. El portal guarda las frases autorizadas para reducir esperas. La música continúa a un volumen reducido durante la locución; si la red o la voz falla, la música sigue sin sustituir la locución por una voz del teléfono. Esta función necesita conexión.

## Actualizar una instalación anterior

Se conserva el paquete `mx.sntss1puebla.credenciales`; la compilación publicada es 0.10.13 (`versionCode` 23). Para actualizar una instalación existente **se necesita la misma clave y certificado con que fue firmado el APK instalado**. No cambies el `applicationId` ni firmes con otra clave. La detección de instalación en Chrome requiere un APK con `asset_statements` y un navegador que admita `getInstalledRelatedApps`.

Mantén el bloque `signingConfigs` de `app/build.gradle.kts` en los siguientes paquetes. Para firmar `release`, proporciona `RADIO_SIGNING_STORE_FILE`, `RADIO_SIGNING_STORE_PASSWORD`, `RADIO_SIGNING_KEY_ALIAS` y `RADIO_SIGNING_KEY_PASSWORD` en el entorno privado de compilación. No agregues la clave, contraseñas ni certificados privados al repositorio. Si faltan esas variables, la configuración `release` no lleva firma: no distribuyas ese APK como actualización.

Los APK 0.2.0, 0.5.0 y 0.8.0 presentan el mismo certificado de firma v2: SHA-256 `89:C6:A0:E7:17:76:D6:22:40:DD:55:BD:CE:ED:9E:DB:E4:6F:E0:2B:71:02:AE:02:D6:FA:FA:E8:D0:BF:64:2E`. Sus `versionCode` son 2, 5 y 8 respectivamente. Otras copias antiguas pueden usar una clave distinta; verifica la firma de la instalación concreta antes de afirmar que se actualiza sin reinstalar. La huella permite comparar certificados, pero no sustituye la clave privada original.

## Compilar

Abre **esta carpeta `android-app`** como proyecto en Android Studio, instala JDK 17, Android SDK 36 y Gradle 8.11.1. Este paquete no trae Gradle Wrapper: usa Gradle instalado o genera el wrapper 8.11.1 desde Android Studio (`gradle wrapper --gradle-version 8.11.1`). El complemento Android usado es 8.10.1. Espera a que termine la sincronización y ejecuta `:app:assembleDebug` para revisar que compila.

Para generar una actualización compatible con el APK 0.5.0, consigue **el almacén y la clave privados originales**; conocer la huella pública no basta para firmar. Configura localmente `RADIO_SIGNING_STORE_FILE` (ruta absoluta), `RADIO_SIGNING_STORE_PASSWORD`, `RADIO_SIGNING_KEY_ALIAS` y `RADIO_SIGNING_KEY_PASSWORD` sin agregarlos al proyecto. En macOS/Linux ejecuta `bash build-radio-release.sh` desde esta carpeta. El script compila `:app:assembleRelease`, verifica la firma del APK con `apksigner` y detiene la entrega si el certificado no coincide con el APK 0.5.0. El resultado queda en `app/build/outputs/apk/release/app-release.apk`. En Windows, configura las mismas variables privadas en Android Studio, compila `assembleRelease` y comprueba con `apksigner verify --verbose --print-certs` la huella indicada arriba.

Comprueba reproducción y los controles de la portada maximizada en un teléfono con navegación por gestos y con botones; prueba el emulador Android Auto y escucha la locución con música activa. El código no incluye MP3, credenciales ni datos de personas. El portal ofrece 0.10.13; verifica la firma antes de reemplazar el APK publicado.

## Gustos musicales (0.10.11)

El botón Mis gustos musicales permite elegir artistas y géneros presentes en el catálogo. Se guardan sólo en el dispositivo. La cola aleatoria da mayor prioridad a los favoritos sin excluir otros temas ni cortar la canción actual; cada vuelta conserva todas las canciones sin duplicarlas. Me gusta de todo y Reiniciar gustos borran ambas selecciones. Android Auto usa la misma cola del teléfono. GitHub Actions compila esta versión con la firma permanente del repositorio y verifica el certificado antes de publicar. Las versiones anteriores requieren instalar esta actualización para usar los gustos musicales.

La selección de gustos se presenta en tarjetas con las portadas disponibles, seis artistas por pantalla, botones Anterior y Siguiente, y un paso final de géneros. Las selecciones se conservan al avanzar y sólo se guardan al finalizar.

## Karaoke (0.10.12)

La portada maximizada incluye el botón Karaoke. Reduce el centro de la mezcla estéreo en el audio del servicio compartido con Android Auto, sin modificar los MP3, y suspende las cápsulas de locución mientras se canta. Al cerrar la vista maximizada se restaura el audio normal. Audio mono y formatos PCM distintos de 16 bits pasan sin procesar. No es separación perfecta de pistas: también puede reducir instrumentos centrados.

Prensa y Administración pueden usar **Sincronizar letra con IA** en la ficha de edición del portal. La IA escucha el MP3 y alinea la letra autorizada completa con los tiempos reconocidos; se rechazan coincidencias insuficientes. Los tiempos son una propuesta: se revisan y guardan antes de publicarlos. La app utiliza la letra LRC guardada y la posición real del reproductor, incluso después de pausar o adelantar.

Se ofrece a Android Auto un control Karaoke en las acciones adicionales (overflow) de reproducción. Su ubicación y visibilidad las decide el automóvil; no existe una señal pública que permita limitarlo exclusivamente a una pantalla “maximizada” del host. El teléfono sí limita su botón al diálogo maximizado. El estado se comparte con la sesión y el audio reducido se transmite a los altavoces del coche.

## Recuperación de reproducción (0.10.13)

Con karaoke apagado se usa el renderer y salida de audio estándar de ExoPlayer. El procesador de karaoke se instala sólo al activar el modo; al cambiar se conserva canción, posición, cola y pausa/reproducción. Si el efecto falla, se restaura el reproductor normal una sola vez. El botón Reproducir vuelve a preparar un reproductor detenido por error y permite reintentar sin cerrar la app. Los errores muestran su código para poder distinguir red, decodificación y salida de audio.


Sin gustos seleccionados, se priorizan canciones menos escuchadas en este dispositivo, y entre ellas las más recientemente agregadas. Solo las reproducciones completas suman al historial local; los comerciales y saltos no se contabilizan. No se envían estadísticas personales. Se conserva la selección explícita de favoritos.
