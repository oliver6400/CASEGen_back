# V12-A — colaboración en tiempo real

## Arquitectura

Las mutaciones continúan realizándose exclusivamente por REST. El servicio que completa la mutación publica un `RealtimeEvent` dentro de la misma transacción y `RealtimeEventBroadcaster` lo entrega mediante STOMP únicamente en `AFTER_COMMIT`.

Canales:

- `/topic/models/{modelId}`: cambios semánticos y cambios de diagramas del modelo.
- `/topic/diagrams/{diagramId}`: cambios gráficos del diagrama concreto.

Endpoint WebSocket/STOMP:

- `/ws`

El token JWT se envía en el header STOMP `CONNECT`:

```text
Authorization: Bearer <access-token>
```

Cada `SUBSCRIBE` vuelve a comprobar acceso de lectura al proyecto. Un proyecto privado requiere miembro activo o `SUPERUSER`; un proyecto público admite suscripción de lectura sin conceder escritura. Las mutaciones siguen verificando permisos en los endpoints REST.

## Configuración Web

En desarrollo, Vite proxifica `/ws` hacia `CASEGEN_DEV_API_TARGET`.

En despliegue puede configurarse explícitamente:

```dotenv
VITE_API_BASE_URL=https://api.example.com
VITE_WS_URL=wss://api.example.com/ws
```

Si `VITE_WS_URL` queda vacío, se deriva desde `VITE_API_BASE_URL` o desde el origen actual.

## Checklist manual

1. Iniciar PostgreSQL, backend y Web.
2. Abrir el mismo diagrama como `OWNER` en Chrome normal.
3. Abrir el mismo diagrama como `COLLABORATOR` en incógnito.
4. Crear `Product` desde OWNER y comprobar que aparece en COLLABORATOR sin F5.
5. Crear `price : Real` desde COLLABORATOR y comprobar que aparece en OWNER.
6. Mover `Product` y comprobar que el otro navegador actualiza la geometría.
7. Verificar en el encabezado que mover el nodo no cambia la revisión semántica.
8. Crear una asociación y comprobar que aparece en el otro navegador.
9. Eliminarla y comprobar que desaparece en el otro navegador.
10. Abrir el proyecto privado con un usuario ajeno y comprobar que la suscripción es rechazada.
11. Cambiar el proyecto a público y comprobar que un visitante solo obtiene actualización de lectura; las mutaciones REST continúan prohibidas.
12. Provocar un conflicto 409 y comprobar el mensaje: `El elemento fue modificado por otro usuario. Se actualizaron los datos.`

No se implementan todavía CRDT/Yjs, cursores remotos, presencia ni escritura por WebSocket.
