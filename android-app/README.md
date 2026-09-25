# SNTSS1Puebla en Android y Android Auto

La app abre el portal oficial en el teléfono y ofrece Radio Sindical como biblioteca musical nativa en Android Auto. Usa Media3 `MediaLibraryService` para mostrar canciones y controles del automóvil. La app necesita una sesión válida iniciada en el portal desde el teléfono; la API del catálogo y el audio requieren esa sesión. No registra cookies ni incluye claves en el repositorio.

## Uso

1. Instala la app en el teléfono y accede a `https://sntss1puebla.com/` con tu cuenta autorizada.
2. Toca **Reproducir Radio Sindical en Android Auto** para iniciar la biblioteca en el teléfono, o conecta Android Auto y abre **Credenciales SNTSS1Puebla > Biblioteca musical**.
3. Puedes seleccionar una canción, avanzar, pausar y reanudar desde los controles del automóvil.

La música se obtiene del catálogo protegido del portal y utiliza 192 kbps cuando esa versión existe; de lo contrario, 320 kbps. El catálogo se vuelve a consultar al abrir la biblioteca. La programación web (comerciales, voz de DeVi, anuncios en vivo y letras) todavía pertenece al reproductor web y no forma parte de esta reproducción nativa. La sesión del portal puede caducar; si deja de verse la biblioteca, vuelve a iniciar sesión desde el teléfono.

## Compilar

1. Abre `android-app` en Android Studio con JDK 17.
2. Instala Android SDK 36 y sincroniza Gradle (el proyecto declara Android Gradle Plugin 8.10.1).
3. Selecciona **Build > Build APK(s)** para pruebas en un teléfono y un entorno Android Auto de desarrollo.
4. Para distribución, firma el paquete con una clave institucional conservada fuera del repositorio y comprueba la experiencia en un vehículo o en Desktop Head Unit.

Paquete `mx.sntss1puebla.credenciales`; Android mínimo 8.0 (API 26). El código no contiene archivos MP3, credenciales ni registros personales.
