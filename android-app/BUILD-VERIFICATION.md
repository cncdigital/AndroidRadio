# Radio Sindical 0.10.30 · controles discretos

Base de revisión: `e28a36d52c6bdc38ba4084159ac4e1d9794305dd` (0.10.29). La candidata usa `versionCode 40` y conserva `mx.sntss1puebla.credenciales` y la firma permanente.

## Cambio solicitado

- Eliminar los botones de selección de fondos y conservar deslizamientos izquierda/derecha.
- En pantalla completa, desvanecer los controles tras inactividad y recuperarlos al tocar, sin desvanecer ni ocultar la letra.
- Mantener controles accesibles, interacción segura con controles ocultos y limpieza de temporizadores al cerrar o pausar la interfaz.
- Conservar el análisis PCM reactivo y el audio existente sin modificaciones.

## Verificación

La base 0.10.29 pasó 41 pruebas JVM debug, 41 release y 17 instrumentadas Android 15. Esos resultados no certifican esta modificación de interfaz.

El workflow de esta candidata vuelve a compilar debug y release, ejecutar unitarias, comprobar firma/versión y ejecutar instrumentadas en emulador API35. Los resultados del run correspondiente al commit son la comprobación vigente. La APK sólo se conserva si las pruebas instrumentadas pasan. El acceso temporal a KVM está limitado al intento autorizado y la ACL se restaura al terminar.

## Límites y distribución

- La verificación en emulador no sustituye uso en teléfono, escucha Bluetooth ni prueba de Android Auto en vehículo.
- Fondos estáticos con silencio, pausa, ahorro de batería o movimiento reducido; sin micrófono ni permiso RECORD_AUDIO.
- Lint global no se declara aprobado por problemas anteriores de opt-in Media3 y filtro de búsqueda Android Auto.
- El PR permanece en borrador. No publicar, fusionar a main, actualizar la rama apk ni crear releases sin autorización.
