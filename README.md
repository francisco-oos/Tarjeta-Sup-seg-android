# Supervisión Segura Android V1

V1 incluye:
- Formulario Android de tarjeta diaria.
- Checklist en true/false.
- Generación de PDF visual.
- Fotos anexas como evidencia.
- JSON interno cifrado con AES-256-GCM.
- QR dentro del PDF para lectura automática.
- Marcador embebido al final del PDF para que el lector de PC extraiga datos sin OCR.
- Control de versión local por fecha y preparado para control remoto.

## Control de versión
En `app/build.gradle` puedes cambiar:

```gradle
versionCode 1
versionName '1.0.0'
buildConfigField 'String', 'LOCAL_EXPIRES_AT', '"2026-12-31"'
buildConfigField 'String', 'VERSION_CONTROL_URL', '""'
```

Si después quieres control remoto, `VERSION_CONTROL_URL` deberá responder algo como:

```json
{
  "enabled": true,
  "min_version_code": 1,
  "expires_at": "2026-12-31",
  "message": "Esta versión ya no está vigente. Solicita actualización."
}
```

## JSON cifrado
El PDF contiene un marcador:

```text
%%SUPERVISION_SEGURA_JSON_ENCRYPTED_BASE64:<payload>
```

El payload trae AES/GCM con `iv_b64` y `data_b64`. En producción la clave debe moverse a un sistema de licencia/Keystore y el lector PC tendrá la clave de descifrado.
