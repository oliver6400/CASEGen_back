# V8.1 — Project Sharing

Los proyectos existentes continúan PRIVATE. PUBLIC es lectura autenticada, no
edición ni acceso anónimo. Los permisos de modelos, diagramas, validación y
versionado usan ProjectAuthorizationService; no se modificó el renderer UML.

## API

- PATCH `/api/projects/{projectId}/visibility`: `{"visibility":"PUBLIC"}` o PRIVATE.
- GET `/api/projects/{projectId}/members`: miembros activos. Emails solo para OWNER/SUPERUSER.
- PATCH `/api/projects/{projectId}/members/{memberId}/role`: `{"role":"VIEWER"}` o COLLABORATOR.
- DELETE `/api/projects/{projectId}/members/{memberId}`: revoca membresía, no borra usuario.
- POST `/api/projects/{projectId}/invitations`: `{"email":"usuario@example.com","role":"COLLABORATOR"}` o VIEWER.
- GET `/api/projects/{projectId}/invitations`: solo OWNER/SUPERUSER.
- GET `/api/me/project-invitations`: invitaciones pendientes y vigentes del email autenticado.
- POST `/api/project-invitations/{invitationId}/accept`: acepta y crea/reactiva membresía.
- POST `/api/project-invitations/{invitationId}/reject`: rechazo por destinatario.
- POST `/api/projects/{projectId}/invitations/{invitationId}/revoke`: revocación por OWNER/SUPERUSER.

No hay transferencia ni invitación de OWNER. Invitaciones caducan en 7 días,
aceptación por email autenticado e ID (no por token/link público). No se exponen
hashes y no se envía correo. El usuario puede registrarse después de ser invitado
usando el mismo email. Una invitación no sustituye una membresía ya activa.
Project Sharing no altera revisiones UML, snapshots ni geometrías.

## Checklist

- [ ] Cuenta A crea proyecto: badge 🔒 PRIVATE. Cuenta B ajena recibe 403 al abrirlo.
- [ ] A cambia a PUBLIC: B ve proyecto/modelos/diagramas, sin edición, validación ni versionado.
- [ ] Acceso sin login continúa bloqueado, incluso con PUBLIC.
- [ ] A invita B como COLLABORATOR; B acepta desde «Mis invitaciones pendientes» y puede editar.
- [ ] A cambia B a VIEWER: B solo lee. A revoca B: PRIVATE vuelve a bloquearlo.
- [ ] A vuelve a invitar B: aceptación reactiva la misma membresía.
- [ ] Invitar otra cuenta como VIEWER: lectura permitida, escritura 403.
- [ ] Destinatario rechaza / A revoca invitación: aceptación posterior 409.
- [ ] COLLABORATOR/VIEWER no pueden invitar, alterar roles o visibilidad.
- [ ] OWNER no tiene controles para eliminarse o cambiar su rol; API tampoco lo permite.
- [ ] SUPERUSER administra cualquier proyecto respetando la protección de OWNER.
- [ ] Validación sin perfil muestra «Modelo UML válido» y «Listo para crear versión» por separado de «Perfil de generación no configurado».
- [ ] Navegación, líneas rectas, flechas, rombos, multiplicidades y labels V7.2 continúan funcionando.
