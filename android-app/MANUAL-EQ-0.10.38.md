# Ecualización manual (Radio Android 0.10.38 / 48)

En el botón central de ajustes de sonido del teléfono, encender Mejoras de audio y
Ecualización. Seleccionar uno de tres modos: Suave, Inteligente o Manual. Manual
muestra cinco deslizadores nominales (120, 300 Hz; 1, 4, 8 kHz) de −3 a +3 dB,
y «Centrar bandas en 0 dB». Cada deslizador tiene nombre y valor de accesibilidad.
El fabricante determina las bandas y límites reales; los ajustes se aproximan
según el centro de cada banda nativa y no superan su margen.

La curva manual se guarda en este dispositivo y se conserva al cambiar de modo.
«Audio original» conserva el comportamiento anterior: restablece todas las
preferencias y desactiva los efectos. Una instalación existente con ajuste
inteligente conserva ese modo; el ajuste previo no inteligente sigue usando
la misma curva suave. El manual nuevo parte plano (0 dB por banda).

Un solo ecualizador de la sesión y un único objetivo de ganancia, sin apilar
inteligente y manual. Cambios con la rampa de audio existente. No afecta al
reproductor de voz ni a los anuncios y no agrega controles a Android Auto.
Durante karaoke se conserva la regla del modo inteligente (usa el perfil suave)
y se suspende la amplitud estéreo; el perfil manual seleccionado y el refuerzo
de graves pueden mantenerse. Se conserva el margen de volumen de la música
existente y se omite el efecto si no es viable en el dispositivo/salida.

Prueba física pendiente: TalkBack y teclado, selección y conservación de los
tres modos, deslizador y reset en media rampa, pérdida/cambio de salida,
control de volumen en cápsula, anuncios y karaoke, sin controles nuevos en Auto.
