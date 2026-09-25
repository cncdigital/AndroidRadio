# Radio Sindical para iPhone y CarPlay

Proyecto nativo SwiftUI para iPhone (iOS 16 o superior). Reproduce el catálogo público de Radio Sindical sin iniciar sesión, muestra la letra disponible y desplaza las líneas que tengan marcas de tiempo LRC. El orden se mezcla al iniciar y al terminar la lista. DeVi presenta por voz el título y artista cada dos canciones completas, y un dato oficial cada cinco. Los comerciales activos se reproducen al terminar una canción después del intervalo configurado por el administrador. El catálogo y la programación se actualizan cada minuto. Los botones para saltar pistas no cuentan como canciones terminadas.

La integración de CarPlay utiliza exclusivamente las plantillas de lista y reproducción de Apple. Las letras aparecen en el iPhone; no se muestran letras ni karaoke en la pantalla del automóvil. El audio y la presentación por voz se reproducen en el sistema de sonido conectado.

El arte de cada canción pasa también a los metadatos `Now Playing` para CarPlay y pantalla bloqueada. La letra sincronizada se destaca con tipografía grande y una línea flotante sobre la portada en el iPhone.

## Compilar en Mac

1. Abre `RadioSindical.xcodeproj` en Xcode 16 o posterior, selecciona el esquema `RadioSindical` y configura tu equipo en **Signing & Capabilities**.
2. Selecciona un iPhone o simulador de iPhone y pulsa **Run**. Para distribuirlo, usa **Product > Archive** y tu cuenta de desarrollador Apple.
3. Para que aparezca en CarPlay, solicita a Apple el entitlement de aplicaciones de audio `com.apple.developer.carplay-audio`. Cuando Apple lo haya autorizado para el identificador de esta app, configura el perfil de aprovisionamiento e incorpora el entitlement indicado en `RadioSindical/CarPlay.entitlements.example` como archivo `.entitlements` en **Signing & Capabilities**. Prueba las plantillas con CarPlay Simulator y, antes de distribuir, con un coche compatible.

El proyecto no activa un entitlement que Apple no haya otorgado. La compilación y firma de iOS requieren macOS con Xcode y credenciales de Apple; no se genera IPA en este entorno. El identificador `mx.sntss1puebla.radiosindical` es un ejemplo: reemplázalo si ya hay un identificador registrado para iPhone.

Las rutas consultadas son `/api/radio/catalog`, `/api/radio/audio/:id`, `/api/radio/commercial/:id`, `/api/radio/cover/:id` y `/api/radio/lyrics/:id`. Sólo muestran contenido activo de escucha; no se utilizan endpoints administrativos ni datos personales. Los anuncios con micrófono en vivo todavía no se reciben en esta app. La reproducción requiere conexión al portal.
