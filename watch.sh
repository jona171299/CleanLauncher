#!/bin/sh
# Vigila src/, res/ y AndroidManifest.xml. Al guardar un cambio, compila
# y reinstala la app en el emulador (o dispositivo) y la vuelve a abrir.
#
# Uso:
#   sh watch.sh                       -> emulador emulator-5554
#   RADIO=192.168.100.17:5555 sh watch.sh   -> otro dispositivo por ADB
#
# Deja esta ventana abierta mientras programas. Para salir: Ctrl + C.

cd "$(dirname "$0")"
SERIAL=${RADIO:-emulator-5554}
ADB=${ADB:-adb}
PKG=com.simple.carlauncher

# Firma de los archivos: cambia cuando cualquiera se guarda.
signature() {
    find src res AndroidManifest.xml -type f -exec stat -c '%Y %n' {} + | sort | md5sum
}

deploy() {
    echo "--- $(date +%H:%M:%S) cambios detectados, compilando..."
    if sh build.sh > build.log 2>&1; then
        $ADB -s "$SERIAL" install -r build/CarLauncher.apk 2>&1 | tail -1
        $ADB -s "$SERIAL" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
        echo "--- $(date +%H:%M:%S) listo"
    else
        echo "--- ERROR de compilacion (detalle en build.log):"
        grep -v WARNING build.log | tail -15
    fi
}

last=""
echo "Vigilando cambios. Dispositivo: $SERIAL"
while true; do
    now=$(signature)
    if [ "$now" != "$last" ]; then
        deploy
        last="$now"
    fi
    sleep 2
done
