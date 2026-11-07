# CASEGen Mobile

Aplicación Flutter de consulta para CASEGen. Permite iniciar sesión, elegir
proyectos, modelos y diagramas, explorar diagramas UML con zoom/desplazamiento,
consultar detalles de clases y seguir presencia y conversación IA en tiempo
real. Es estrictamente de solo lectura para el modelo: no ofrece controles de
creación, edición ni eliminación; el chat móvil tampoco permite enviar mensajes.

El cliente usa endpoints de lectura y presencia STOMP con actividad `VIEWING`.
La autorización de proyectos y diagramas sigue siendo responsabilidad del
backend.

## Ejecutar

Con el backend disponible en el host y un emulador Android:

```powershell
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Para Flutter Web, la URL se resuelve automáticamente desde el host actual.
También puede fijarse de forma explícita:

```powershell
flutter run -d chrome --web-port=60005 `
  --dart-define=API_BASE_URL=http://localhost:8080
```

En modo debug, las peticiones aparecen en la consola de Chrome con el prefijo
`[CASEGen HTTP]`. Los logs no incluyen credenciales, tokens, headers ni bodies.

Para un dispositivo físico, use una URL HTTPS desplegada o la IP accesible del
equipo donde se ejecuta el backend. No incluya credenciales en `API_BASE_URL`.
La conexión WebSocket usa automáticamente `ws://` o `wss://` y añade `/ws` a
la URL base.

### Teléfono en la misma red Wi-Fi

Para abrir la versión móvil en Chrome del teléfono, use
`http://<IP-del-equipo>:5000` (por ejemplo, `http://192.168.0.104:5000`).
El servidor móvil reenvía `/api` y `/ws` al backend local en el puerto `8080`;
el teléfono no necesita conectarse directamente a ese puerto.

Si el teléfono no puede abrir la página, permita conexiones TCP entrantes al
puerto `5000` en el firewall de Windows, solo en una red privada y confiable.
No exponga el puerto `8080` a la red cuando se use este proxy.

Para ejecutar la app Android debug en un dispositivo físico:

```powershell
flutter run -d <id-del-dispositivo> `
  --dart-define=API_BASE_URL=http://<IP-del-equipo>:5000
```

`10.0.2.2` es solo para emuladores Android. Los builds debug permiten HTTP para
probar contra la IP local; los builds release mantienen HTTP deshabilitado y
deben usar HTTPS.

## Verificación

```powershell
flutter analyze
flutter test
```

## Recursos de Flutter

Guías oficiales para desarrollar y ejecutar la aplicación:

A few resources to get you started if this is your first Flutter project:

- [Learn Flutter](https://docs.flutter.dev/get-started/learn-flutter)
- [Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Flutter learning resources](https://docs.flutter.dev/reference/learning-resources)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
