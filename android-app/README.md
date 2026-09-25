# Radio Sindical para Android y Android Auto

La app abre directamente el reproductor nativo, sin WebView ni inicio de sesión. Android Auto muestra el catálogo público de canciones activas mediante `MediaLibraryService`. Las rutas públicas `/api/radio/catalog` y `/api/radio/audio/:id` no exponen archivos administrativos ni comerciales; la administración y el catálogo privado del portal siguen protegidos.

La pantalla del **teléfono** muestra la letra cargada y sigue las líneas que tengan marcas de tiempo. Android Auto mantiene su pantalla de reproducción estándar con título, artista y controles; no incorpora letras desplazables en la pantalla del vehículo. DeVi presenta por voz la siguiente canción después de cada dos canciones terminadas, usando el título y el artista conocidos. La voz nativa usa el motor de texto a voz instalado en el dispositivo.

## Actualizar una instalación anterior

Se conserva el paquete `mx.sntss1puebla.credenciales`; esta versión es `versionCode` 3. Para actualizar una instalación existente **se necesita la misma clave y certificado con que fue firmado el APK instalado**. Compila y firma desde el mismo entorno/keystore usado para ese APK. No cambies el `applicationId` ni firmes con otra clave.

El certificado del APK que se compartió tiene huella SHA-256 `6D:64:06:B1:FF:DE:EB:8D:93:94:F4:50:84:27:BE:3A:14:B6:DB:12:5E:E5:9F:06:FB:E3:71:B9:38:AE:90:AE`. Comprueba esta huella en el APK nuevo antes de ofrecerlo como actualización. La huella permite verificar la firma; no sustituye la clave privada original.

El APK publicado posteriormente como `RadioSindical-0.2.0.apk` usa **otra firma** (`AF:39:0D:09:40:C4:1C:A0:16:7F:86:8E:72:15:12:0F:F8:F5:A9:2E:7B:62:89:14:D3:86:85:43:38:7C:D7:E3`). Si estás actualizando la versión 0.2.0, usa su propia clave y comprueba que la huella del APK 0.3.0 coincida con esa segunda firma. No publiques un APK 0.3.0 como actualización hasta verificarlo.

## Compilar

Abre `android-app` en Android Studio, usa JDK 17 y Android SDK 36, sincroniza Gradle y crea el APK con la clave original. Comprueba reproducción en teléfono y en el emulador Android Auto antes de distribuirlo. El código no incluye MP3, credenciales ni datos de personas.
