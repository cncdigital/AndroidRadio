# Radio Sindical para Android y Android Auto

La app abre directamente el reproductor nativo, sin WebView ni inicio de sesión. Android Auto muestra canciones activas mediante `MediaLibraryService`, con una cola distinta al iniciar, datos de DeVi cada cinco canciones terminadas, presentación de cada canción y comerciales al alcanzar el intervalo administrado al terminar una canción. El catálogo se actualiza cada minuto sin cortar el tema actual. Las rutas públicas de escucha sólo sirven canciones y comerciales activos; la administración y el catálogo privado del portal siguen protegidos. Los avisos de micrófono en vivo requieren una integración de audio específica y todavía no se escuchan desde esta app.

Las portadas se entregan a Android Auto mediante `RadioArtworkProvider`, que descarga y conserva brevemente las imágenes activas; el automóvil muestra el arte en su reproductor de sistema. En el teléfono, la línea de letra con marca de tiempo aparece ampliada sobre la portada y en el panel de karaoke. Por seguridad vial, no se coloca la letra en la pantalla de Android Auto.

La pantalla del **teléfono** muestra la letra cargada y sigue las líneas que tengan marcas de tiempo. Android Auto mantiene su pantalla de reproducción estándar con título, artista y controles; no incorpora letras desplazables en la pantalla del vehículo. DeVi presenta por voz cada canción, usando el título y el artista conocidos. La voz nativa usa el motor de texto a voz instalado en el dispositivo.

El teléfono tiene el botón **Maximizar portada**. Abre una vista de pantalla completa con la portada, la línea sincronizada, controles de reproducción y un botón para volver; mantiene la pantalla encendida sólo mientras está abierta. Android Auto utiliza la interfaz de reproducción del automóvil y no admite este diálogo propio en la pantalla del vehículo.

En las locuciones musicales se elige la voz española de mayor calidad instalada o disponible en el dispositivo; si la voz de red tarda o falla, se intenta la mejor voz local. Si no hay una voz de buena calidad instalada, el resultado sigue dependiendo del motor TTS del teléfono. Se mantiene la reproducción si falla la narración.

## Actualizar una instalación anterior

Se conserva el paquete `mx.sntss1puebla.credenciales`; el código de esta versión es `versionCode` 8. El APK distribuido actualmente en el portal sigue siendo 0.5.0 (`versionCode` 5) hasta que se compile y firme 0.8.0. Para actualizar una instalación existente **se necesita la misma clave y certificado con que fue firmado el APK instalado**. Compila y firma desde el mismo entorno/keystore usado para ese APK. No cambies el `applicationId` ni firmes con otra clave. La detección de instalación en Chrome requiere el nuevo APK 0.8.0 con `asset_statements` y un navegador que admita `getInstalledRelatedApps`.

Mantén el bloque `signingConfigs` de `app/build.gradle.kts` en los siguientes paquetes. Para firmar `release`, proporciona `RADIO_SIGNING_STORE_FILE`, `RADIO_SIGNING_STORE_PASSWORD`, `RADIO_SIGNING_KEY_ALIAS` y `RADIO_SIGNING_KEY_PASSWORD` en el entorno privado de compilación. No agregues la clave, contraseñas ni certificados privados al repositorio. Si faltan esas variables, la configuración `release` no lleva firma: no distribuyas ese APK como actualización.

El APK histórico `RadioSindical-0.2.0.apk` y el actual `RadioSindical-0.5.0.apk` presentan el mismo certificado de firma v2: SHA-256 `89:C6:A0:E7:17:76:D6:22:40:DD:55:BD:CE:ED:9E:DB:E4:6F:E0:2B:71:02:AE:02:D6:FA:FA:E8:D0:BF:64:2E`. El APK 0.5.0 tiene `versionCode` 5 frente a 2 en el 0.2.0. Otras copias antiguas pueden usar una clave distinta; verifica la firma de la instalación concreta antes de afirmar que se actualiza sin reinstalar. La huella permite comparar certificados, pero no sustituye la clave privada original.

## Compilar

Abre **esta carpeta `android-app`** como proyecto en Android Studio, instala JDK 17, Android SDK 36 y Gradle 8.11.1. Este paquete no trae Gradle Wrapper: usa Gradle instalado o genera el wrapper 8.11.1 desde Android Studio (`gradle wrapper --gradle-version 8.11.1`). El complemento Android usado es 8.10.1. Espera a que termine la sincronización y ejecuta `:app:assembleDebug` para revisar que compila.

Para generar una actualización compatible con el APK 0.5.0, consigue **el almacén y la clave privados originales**; conocer la huella pública no basta para firmar. Configura localmente `RADIO_SIGNING_STORE_FILE` (ruta absoluta), `RADIO_SIGNING_STORE_PASSWORD`, `RADIO_SIGNING_KEY_ALIAS` y `RADIO_SIGNING_KEY_PASSWORD` sin agregarlos al proyecto. En macOS/Linux ejecuta `bash build-radio-release.sh` desde esta carpeta. El script compila `:app:assembleRelease`, verifica la firma del APK con `apksigner` y detiene la entrega si el certificado no coincide con el APK 0.5.0. El resultado queda en `app/build/outputs/apk/release/app-release.apk`. En Windows, configura las mismas variables privadas en Android Studio, compila `assembleRelease` y comprueba con `apksigner verify --verbose --print-certs` la huella indicada arriba.

Comprueba reproducción en teléfono y en el emulador Android Auto antes de distribuirlo. El código no incluye MP3, credenciales ni datos de personas. El portal seguirá ofreciendo 0.5.0 hasta que se cargue el nuevo APK firmado.
