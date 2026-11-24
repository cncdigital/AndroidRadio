# Enlaces de video revisados (Radio Android 0.10.37 / 47)

El catálogo oficial ya consultado por Radio puede agregar en canciones `youtubeVideoId`
(ID público de 11 caracteres) y `youtubeVideoVersion` (`official` o `live`).
El servidor entrega exclusivamente asociaciones revisadas. `youtubeVideoDisabled: true`
suprime toda asociación para esa pista, incluidas las dos del piloto 0.10.36.
Falta de metadatos (servidor antiguo) conserva solo las dos coincidencias exactas del piloto.
Ni la app ni el servidor deben considerar un candidato como confirmado por título aproximado.

El teléfono ofrece el enlace externo únicamente en el giro de fondos de la pista correspondiente.
El servicio verifica pista, ID y versión contra la última respuesta del catálogo, separada
de la cola, antes de pausar y confirmar al teléfono. No abre video al deslizar ni en
Android Auto. La única URI posible se genera localmente como
`https://www.youtube.com/watch?v=<ID>`: no se aceptan URL del catálogo ni se extrae audio.
Sin conexión o si se usa el respaldo local, los enlaces agregados remotamente no se revalidan;
se respeta una supresión registrada en la copia. No se agrega red ni reproducción en la app.

Una renovación del catálogo actualiza la cola futura, sin reemplazar la pista sonando ni
reiniciar letras o posición de reproducción. Un video reasignado o retirado en el
catálogo puede seguir apareciendo en la pista activa hasta el próximo cambio de pista;
si el catálogo ya se renovó, el servicio rechaza abrir un enlace obsoleto.
La app anterior ignora el contrato nuevo y conserva sus dos asociaciones compiladas.

Prueba requerida en teléfono: pista con video oficial, pista con versión en vivo,
pista sin asociación, Retirar/Reasignar en portal de pruebas con la pista ya activa,
navegación sin red, apertura tras deslizar, salida del navegador sin reanudar automáticamente,
sin otro sonido/cápsula al iniciar YouTube, letras y posición guardadas, y ausencia
del acceso de video en Android Auto. No hay cobertura general garantizada.
