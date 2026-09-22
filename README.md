# CASEGen

## Importación de diagramas UML desde imágenes

El análisis se ejecuta exclusivamente en el backend. El proveedor principal y predeterminado es
`GEMINI`; recibe la imagen original completa y devuelve una propuesta estructurada que el usuario revisa
antes de confirmar. `LOCAL` (OpenCV + Tesseract) permanece disponible como selección explícita y como
fallback de Gemini ante timeout, cuota `429`, error `5xx` o indisponibilidad temporal. Un error `401/403`
se trata como configuración inválida y no activa el fallback. Las claves nunca deben exponerse en React
ni utilizar el prefijo `VITE_`.

Variables soportadas:

```env
IMAGE_IMPORT_PROVIDER=GEMINI
IMAGE_IMPORT_MAX_BYTES=10485760
IMAGE_IMPORT_LOCAL_FALLBACK=true
TESSERACT_COMMAND=tesseract
TESSERACT_LANGUAGE=eng
IMAGE_IMPORT_LOCAL_MIN_WIDTH_RATIO=0.07
IMAGE_IMPORT_LOCAL_MIN_HEIGHT_RATIO=0.045
IMAGE_IMPORT_LOCAL_MIN_AREA_RATIO=0.004
IMAGE_IMPORT_LOCAL_MIN_RECTANGULARITY=0.42
IMAGE_IMPORT_LOCAL_MIN_ASPECT_RATIO=0.55
IMAGE_IMPORT_LOCAL_MAX_ASPECT_RATIO=5.5

GEMINI_API_KEY=
GEMINI_MODEL=gemini-2.5-flash
GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1beta

# Alternativa OPENAI
IMAGE_IMPORT_API_KEY=
IMAGE_IMPORT_API_URL=https://api.openai.com/v1/responses
IMAGE_IMPORT_MODEL=gpt-4o-mini
```

`LOCAL` no requiere ninguna API key. Para seleccionarlo expresamente use
`IMAGE_IMPORT_PROVIDER=LOCAL`. OpenCV se distribuye con el backend; para extraer texto debe
instalar Tesseract OCR y dejar `tesseract` disponible en `PATH`, o indicar su ejecutable con
`TESSERACT_COMMAND`. Si Tesseract no está disponible, el análisis geométrico sigue funcionando y el
preview muestra nombres temporales (`Clase1`, `Clase2`, ...) y advertencias para su corrección.
CASEGen usa el ejecutable local de Tesseract (no Tess4J). En una imagen Docker del backend debe
instalarse el paquete `tesseract-ocr` y el paquete de idioma indicado por `TESSERACT_LANGUAGE`.
El endpoint `GET /api/image-import/status` informa `provider`, `configured`, `model`,
`fallbackLocalAvailable` y `localOcrAvailable` sin exponer secretos.
Los umbrales `IMAGE_IMPORT_LOCAL_*` son relativos a la imagen y normalmente no necesitan ajustes.

Para usar Gemini configure `GEMINI_API_KEY`; `IMAGE_IMPORT_PROVIDER=GEMINI` ya es el valor por defecto. Para usar
OpenAI seleccione `IMAGE_IMPORT_PROVIDER=OPENAI` y configure `IMAGE_IMPORT_API_KEY`.
El archivo `.env` local está ignorado por Git; Spring Boot recibe sus valores como variables del proceso.

Desde PowerShell, en `backend/`:

```powershell
Get-Content ..\.env |
  Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } |
  ForEach-Object {
    $parts = $_.Split('=', 2)
    [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
  }

.\mvnw.cmd spring-boot:run
```
