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

## Clave de cifrado

La clave real **no se guarda en Git**. El build la toma, en este orden, de:

1. La variable de entorno `SUPERVISION_SEGURA_DATA_KEY`.
2. El archivo local `supervision-secrets.properties` (ignorado por Git).
3. El placeholder `CHANGE_ME_SUPERVISION_SEGURA_DATA_KEY` sólo para desarrollo.

Ejemplo local:

```properties
SUPERVISION_SEGURA_DATA_KEY=coloca-aqui-tu-clave-real
```

La misma clave debe configurarse en Supervisión Segura Manager para poder descifrar los PDF. No uses el placeholder para información real.

## Control de versión

En `app/build.gradle` puedes cambiar:

```gradle
versionCode 2
versionName '1.0.1'
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

El payload trae AES/GCM con `iv_b64` y `data_b64`. La clave se inyecta en compilación desde configuración local/entorno y no forma parte del repositorio.
