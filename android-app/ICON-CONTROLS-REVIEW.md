# Radio Sindical 0.10.31 / código 41 · candidata

Actualización sobre la candidata 0.10.30. Mantiene applicationId y firma permanente.

- Cerrar, minimizar y maximizar son íconos de 48 dp en la esquina superior derecha, en una cabecera fija. La cabecera y el contenido respetan barras del sistema y recortes.
- Anterior y siguiente tienen controles circulares; reproducir/pausar destaca en dorado. Íconos vectoriales, estados de foco/pulsación/deshabilitado y nombres accesibles. El símbolo reproduce el estado real de reproducción.
- Los mismos controles de transporte aparecen en pantalla completa. Restaurar usa ícono; siguen funcionando desaparición/recuperación de controles, letra y gesto de fondos.
- No cambia servicio musical, caché, biblioteca, permisos ni Android Auto.

## Verificación

La CI compila debug/release, ejecuta unitarias y verifica certificado/versión. Pruebas instrumentadas cubren 320/420/600 dp, cabecera fija, íconos y acciones accesibles, transporte y captura sintética; la suite anterior sigue comprobando pantalla completa. El emulador solo se ejecuta si KVM ya es accesible: no cambia permisos. Si no lo es, el resultado se comunica como no ejecutado, no aprobado.

No distribuir de forma general ni actualizar main, rama apk o releases. Esta APK es candidata para Christian; requiere prueba en teléfono y no implica validación en vehículo.
