# Radio Sindical 0.10.28 · candidata de prueba

Base: `cncdigital/AndroidRadio`, commit `01bfb1cd44255fb3652fbabb60418eb664cb5ba9` (0.10.27). La candidata utiliza `versionCode 38` y conserva el paquete `mx.sntss1puebla.credenciales`.

## Comprobaciones realizadas

- JDK 17, Gradle 8.14.3, Android SDK 36 y Build-Tools 36.0.0 oficiales.
- `testDebugUnitTest`: **33 pruebas aprobadas**, sin fallos ni pruebas omitidas.
- `assembleDebug` y `assembleDebugAndroidTest`: aprobados.
- Recursos XML válidos y `git diff --check` sin errores.
- `aapt` confirma versión 0.10.28 / código 38.
- `apksigner` confirma firma v2 de depuración. No es la firma permanente de distribución y no debe ofrecerse como actualización de una instalación existente.
- `lintDebug`: 104 errores existentes en la base intacta y en la candidata, sin errores nuevos. Son 103 usos de API experimental Media3 sin anotación y un filtro de búsqueda de Android Auto ausente. También hay advertencias de estilo/textos. Lint global no está aprobado.
- Revisión adicional de código completada. Se corrigieron la intercepción del gesto por el desplazamiento vertical, el orden de restauración de volumen al apagar efectos y la revalidación/reintento de efectos para cambios de salida.

APK de depuración, SHA-256:
`ae71a792b06271252235b3024083570d1fe3530630fd24ed70fc3025cdea8231`

## Comprobaciones pendientes antes de distribuir

- Ejecutar las pruebas instrumentadas en Android. No llegaron a ejecutarse localmente: un emulador terminó con SIGKILL y el último intento no completó el arranque. La compilación de las pruebas no demuestra que se hayan ejecutado.
- Probar visualizadores, letra, desplazamiento, pantalla completa, pausas y reapertura en un teléfono real.
- Escuchar los efectos en altavoz, auriculares y Bluetooth, y comprobar karaoke y anuncios. Una prueba unitaria no certifica una mejora audible, la capacidad del hardware ni ausencia absoluta de distorsión.
- Verificar Android Auto con la interfaz estándar del vehículo.
- Compilar con la firma permanente, comprobar su certificado y autorizar la publicación. La actualización no se ha publicado en la rama de descargas ni como release. La rama de revisión conserva los cambios y puede generar una candidata como artefacto de CI.

El workflow de la rama `main` publica automáticamente. Las pruebas de CI deben mantenerse en una rama separada y sin el paso de publicación hasta autorizar la entrega.
