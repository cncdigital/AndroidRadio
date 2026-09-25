# Compilar Radio Sindical para Android Auto

## Qué contiene el paquete

`android-app/` es el proyecto Android actualizado: muestra únicamente el reproductor, se llama **Radio Sindical**, tiene icono nuevo y consulta canciones activas sin pedir inicio de sesión. El servicio del portal para esas canciones ya está publicado. **Este paquete es código fuente, no un APK actualizado.**

## Preparar la computadora

1. Instala **Android Studio** y el **Android SDK 36**. Usa **JDK 17**. El proyecto declara Android Gradle Plugin **8.10.1**, compatible con Gradle **8.11.1**.
2. Descomprime el archivo ZIP y abre **la carpeta `android-app`** desde **Android Studio > Open**.
3. Este proyecto no trae Gradle Wrapper. Si Android Studio solicita una distribución de Gradle, selecciona **Gradle 8.11.1**. Si tienes Gradle instalado en tu computadora, puedes generar el wrapper desde la terminal abierta en `android-app` con `gradle wrapper --gradle-version 8.11.1` y luego sincronizar de nuevo.
4. Espera a que se descarguen las dependencias y termine **Sync Project with Gradle Files**.
5. Selecciona **Build > Build Bundle(s) / APK(s) > Build APK(s)**. Para una compilación de prueba, el resultado suele quedar en `android-app/app/build/outputs/apk/debug/app-debug.apk`.

## Actualizar la app instalada, sin duplicarla

La app conserva el identificador `mx.sntss1puebla.credenciales` y ahora usa `versionCode 2`. **El APK nuevo debe firmarse con la misma clave privada que el APK anterior**. Para la compilación de prueba, usa la misma computadora y el mismo perfil de Android Studio con los que se creó `RadioSindical-debug.apk`. La clave de depuración suele estar en `~/.android/debug.keystore` (Windows: `%USERPROFILE%\.android\debug.keystore`). Si el APK anterior se creó en otra computadora, se necesita su archivo de firma original: no puede obtenerse a partir del APK.

La huella SHA-256 del certificado del APK anterior es:

`6D:64:06:B1:FF:DE:EB:8D:93:94:F4:50:84:27:BE:3A:14:B6:DB:12:5E:E5:9F:06:FB:E3:71:B9:38:AE:90:AE`

Antes de instalar el nuevo APK, comprueba su firma con la herramienta **apksigner** del Android SDK:

```sh
apksigner verify --print-certs app-debug.apk
```

El valor `Signer #1 certificate SHA-256 digest` debe coincidir con la huella indicada (separadores y mayúsculas pueden variar). Si no coincide, **no desinstales la app anterior**: ese APK no servirá como actualización. La clave debe guardarse en privado y fuera del repositorio.

## Comprobación final

Instala el APK nuevo encima de la versión anterior en un teléfono Android. Comprueba que se llame **Radio Sindical**, muestre el reproductor sin login, reproduzca, pause y avance; después conecta Android Auto y prueba la biblioteca. Si el teléfono informa conflicto de firma, revisa el certificado antes de repetir la instalación.

El APK anterior que aparece en el portal aún abre la versión previa. No lo confundas con esta actualización pendiente de compilación.
