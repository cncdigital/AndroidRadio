# Fondos animados del reproductor

Implementación sobre AndroidRadio `01bfb1cd44255fb3652fbabb60418eb664cb5ba9` (0.10.27). No modifica la reproducción, las cápsulas, la caché, la sincronización de letra ni Android Auto. No hay APK nuevo publicado con este cambio todavía.

## Uso

- Desliza horizontalmente la portada del teléfono, tanto en vista normal como maximizada, para recorrer Portada original, Barras neón, Ondas, Túnel, Aurora y Estrellas.
- El botón «Fondo» también permite elegir directamente, sin depender del gesto. La opción se guarda en este dispositivo y se comparte entre las dos vistas.
- La letra permanece en su capa superior. En pantalla completa, arrastrar la letra o «Mover letra» sigue moviendo la letra, sin cambiar el fondo. El cambio de fondo se hace en el espacio libre de la portada.
- Tocar anterior/siguiente sigue cambiando la canción. Un gesto de fondo no busca otra canción, no cambia la posición ni reinicia el audio.
- Son animaciones decorativas inspiradas en los visualizadores clásicos. No analizan el audio ni usan el micrófono, permisos adicionales o datos móviles.
- La animación se detiene al pausar, minimizar, ocultar la vista o entrar al ahorro de batería. Respeta la opción de desactivar animaciones de Android. La imagen sigue visible; seleccionar Portada original restaura la carátula.

## Verificación antes de entregar APK

Requiere JDK 17, Android SDK 36/build-tools 36.0.0 y Gradle 8.14.3, como el workflow del repositorio.

1. `gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon`.
2. En emulador/teléfono: `gradle connectedDebugAndroidTest --no-daemon`.
3. Con una canción y letra sincronizada: cambiar todos los fondos en ambos sentidos, abrir/cerrar/maximizar repetidamente, mover la letra, pausar/reanudar, buscar otra posición, cambiar canción y volver a abrir la app. Verificar letra y preferencias.
4. Probar desplazamiento vertical de la pantalla normal, gesto cancelado, varias pulsaciones, dos dedos, botón Fondo y TalkBack. Los gestos verticales y cancelados no deben seleccionar un fondo.
5. Probar ahorro de batería, animaciones de Android desactivadas, app minimizada, pantalla bloqueada, rotación y modos de navegación por gestos/botones. No debe mantenerse un bucle de dibujo oculto.
6. Confirmar en un dispositivo que Android Auto conserva su interfaz estándar.

Las pruebas nuevas cubren la selección cíclica, preferencias desconocidas, umbral/dirección de gestos, cancelación, capas de letra y el dibujo de los cinco estilos. Las pruebas instrumentadas requieren Android; una revisión estática no las sustituye.

Antes de publicar, asignar una versión mayor a la instalada, compilar con la firma permanente y verificarla. El workflow `build-apk.yml` publica automáticamente al hacer push a `main`; no usar esa rama sólo para probar este parche.
