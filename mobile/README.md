# CASEGen Mobile

Base funcional móvil de CASEGen. Consume la autenticación JWT y los recursos
de proyectos/modelos del backend Spring Boot existente. En esta etapa el
asistente reconoce comandos localmente, pero no ejecuta mutaciones UML.

## Ejecutar

Con el backend disponible en el host y un emulador Android:

```powershell
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Para Flutter Web, la URL se resuelve automáticamente a
`http://localhost:8080`. También puede fijarse de forma explícita:

```powershell
flutter run -d chrome --web-port=60005 `
  --dart-define=API_BASE_URL=http://localhost:8080
```

En modo debug, las peticiones aparecen en la consola de Chrome con el prefijo
`[CASEGen HTTP]`. Los logs no incluyen credenciales, tokens, headers ni bodies.

Para un dispositivo físico, use una URL HTTPS desplegada o la IP accesible del
equipo donde se ejecuta el backend. No incluya credenciales en `API_BASE_URL`.

## Verificación

```powershell
flutter analyze
flutter test
```

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Learn Flutter](https://docs.flutter.dev/get-started/learn-flutter)
- [Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Flutter learning resources](https://docs.flutter.dev/reference/learning-resources)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
