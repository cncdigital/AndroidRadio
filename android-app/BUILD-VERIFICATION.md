# Radio Sindical 0.10.29 · candidata reactiva

Base: `cncdigital/AndroidRadio` y candidata de revisión `dfab37b397e8cbadffc48a50d1b1bb70776f4dd7` (0.10.28). La candidata utiliza `versionCode 39` y conserva el paquete `mx.sntss1puebla.credenciales`.

## Alcance

La visualización usa energía y frecuencias del PCM real de la música, sin micrófono ni permisos adicionales. Conserva letras, karaoke y efectos opcionales. El silencio no produce un ritmo simulado.

## Verificación y recuperación

La implementación anterior a la recuperación pasó 41 pruebas JVM, compiló ambos APKs de depuración y redujo los errores de lint de 104 a 93 sin añadir errores. El entorno que contenía esos archivos se sustituyó. Se recuperaron la base desde GitHub y las ediciones desde sus comandos conservados. Esos resultados anteriores no certifican por sí solos la copia recuperada.

El workflow de revisión vuelve a ejecutar las pruebas JVM y las compilaciones debug/release, verifica la firma permanente y versión, y ejecuta las pruebas instrumentadas en Android API 35. Los resultados de la ejecución correspondiente al commit son la comprobación vigente. La APK se conserva sólo si pasan las pruebas instrumentadas.

La autorización de KVM está limitada a la ejecución 2, intento 1: acceso temporal a `/dev/kvm` sólo para el usuario runner y restauración de su ACL original al finalizar. No hay cambios a `main`, a la rama de descargas `apk` ni releases.

## Cobertura

- PCM sin modificación, energía/pico, frecuencia, antifase, silencio, reloj, caducidad, historial acotado y tasas de muestreo.
- Fábrica de audio real: normal/karaoke, pausa, comerciales, búsqueda a silencio/agudos y cambio de pista.
- Gestos, desplazamiento vertical, capas de letras y dibujo reactivo.

## Límites antes de distribución general

- El emulador no sustituye la escucha y prueba en teléfono con altavoz, auriculares y Bluetooth, ni la verificación de Android Auto en el vehículo.
- La reacción usa PCM previo al volumen, ducking y efectos del dispositivo. La latencia adicional de cada salida Bluetooth no está garantizada.
- La lectura de PCM desactiva passthrough/offload de música. FFT se ejecuta sólo con un fondo visible, reproducción activa y movimiento permitido.
- Una prueba automática no certifica mejora audible, capacidad del hardware ni ausencia absoluta de distorsión.
- Lint global sigue pendiente por errores previos de Media3 y un filtro de búsqueda de Android Auto ausente.
- El workflow de `main` publica automáticamente. Mantener la revisión separada hasta autorizar publicación y distribución.
