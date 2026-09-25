#!/usr/bin/env bash
set -euo pipefail

# Run from any directory on macOS/Linux with Gradle, Android SDK and the original signing key.
cd "$(dirname "$0")"

for name in RADIO_SIGNING_STORE_FILE RADIO_SIGNING_STORE_PASSWORD RADIO_SIGNING_KEY_ALIAS RADIO_SIGNING_KEY_PASSWORD; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Falta %s en el entorno privado de compilación.\n' "$name" >&2
    exit 1
  fi
done
if [[ ! -f "$RADIO_SIGNING_STORE_FILE" ]]; then
  printf 'No existe el almacén de firma especificado.\n' >&2
  exit 1
fi
if [[ "$RADIO_SIGNING_STORE_FILE" != /* ]]; then
  printf 'RADIO_SIGNING_STORE_FILE debe ser una ruta absoluta.\n' >&2
  exit 1
fi

if [[ -x ./gradlew ]]; then
  gradle_cmd=(./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  gradle_cmd=(gradle)
else
  printf 'Instala Gradle 8.11.1 o genera Gradle Wrapper 8.11.1 desde Android Studio.\n' >&2
  exit 1
fi

if ! command -v apksigner >/dev/null 2>&1; then
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    apk_signer="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name apksigner -type f 2>/dev/null | sort -V | tail -1)"
  fi
  if [[ -z "${apk_signer:-}" ]]; then
    printf 'Falta apksigner: instala Android SDK Build Tools y expórtalo en PATH o ANDROID_HOME.\n' >&2
    exit 1
  fi
else
  apk_signer="$(command -v apksigner)"
fi

"${gradle_cmd[@]}" :app:assembleRelease
apk="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apk" ]]; then
  printf 'No se generó %s.\n' "$apk" >&2
  exit 1
fi

signature="$("$apk_signer" verify --verbose --print-certs "$apk")"
expected="89C6A0E71776D62240DD55BDCEED9EDBE46FE02B7102AE02D6FAFAE8D0BF642E"
actual="$(printf '%s\n' "$signature" | sed -n 's/.*Signer #1 certificate SHA-256 digest: //p' | head -1 | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]')"
if [[ "$actual" != "$expected" ]]; then
  printf 'Firma diferente de la versión 0.5.0. No distribuyas este APK como actualización.\n' >&2
  exit 1
fi

printf 'APK 0.10.0 compilado, verificado y firmado con el certificado esperado: %s\n' "$apk"
