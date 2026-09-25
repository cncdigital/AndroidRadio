# Radio Sindical para Android y Android Auto

La app abre directamente el reproductor nativo, sin WebView ni inicio de sesión. Android Auto muestra el catálogo público de canciones activas mediante `MediaLibraryService`. Las rutas públicas `/api/radio/catalog` y `/api/radio/audio/:id` no exponen archivos administrativos ni comerciales; la administración y el catálogo privado del portal siguen protegidos.

## Actualizar una instalación anterior

Se conserva el paquete `mx.sntss1puebla.credenciales`; la nueva versión incrementa `versionCode` de 1 a 2. Para instalar la actualización encima del APK anterior **se necesita la misma clave y certificado con que fue firmado** `RadioSindical-debug.apk`. Compila y firma desde el mismo entorno/keystore usado para el APK anterior. No cambies el `applicationId` ni firmes con otra clave. El nombre y el icono se actualizan al instalar la nueva versión; el sistema podría pedir al usuario autorizar la actualización, pero no instalará una segunda app.

El certificado del APK que se compartió tiene huella SHA-256 `6D:64:06:B1:FF:DE:EB:8D:93:94:F4:50:84:27:BE:3A:14:B6:DB:12:5E:E5:9F:06:FB:E3:71:B9:38:AE:90:AE`. Comprueba esta huella en el APK nuevo antes de ofrecerlo como actualización. La huella permite verificar la firma; no sustituye la clave privada original.

## Compilar

Abre `android-app` en Android Studio, usa JDK 17 y Android SDK 36, sincroniza Gradle y crea el APK con la clave original. Comprueba reproducción en teléfono y en el emulador Android Auto antes de distribuirlo. El código no incluye MP3, credenciales ni datos de personas.
