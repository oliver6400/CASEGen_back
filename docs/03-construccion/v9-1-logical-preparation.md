# V9.1 — Preparación automática del modelo lógico

El flujo normal no requiere JSON ni UUID: seleccionar versión/perfil, revisar
sugerencias e identificadores, guardar preparación, construir y ver resultado.

## UI

- Perfil: nombre, SPRING_BOOT, POSTGRESQL, versiones Java/Spring Boot, groupId,
  artifactId, basePackage e inheritance strategy JOINED. Se conservan los valores
  existentes y otras claves config_json al editar.
- Todos los Classifiers de ModelVersion aparecen; CLASS sugiere ENTITY,
  INTERFACE sugiere JAVA_INTERFACE. IGNORE se puede elegir explícitamente.
- Identificador se elige por nombre entre Properties válidas de la clase y sus
  ancestros JOINED. Nunca aparecen opciones de otras clases sin relación.
  Un único id (case-insensitive) propio se preselecciona como sugerencia; si no hay
  propio, un único id heredado puede sugerirse. Configuraciones guardadas no se
  sobrescriben por una sugerencia.
- FK 1:N/N:1, join N:M y composición se resuelven con las reglas V9 existentes.
  Dependencia clara en 1:1 significa metadata classifier-owned inequívoca.
  Una 1:1 realmente ambigua pide «Propietario de FK» con nombres de clases;
  una self 1:1 también requiere el extremo referenciado.
- No se usa el número de FK ni navigability para inventar ownership de base de datos.
- Resumen previo muestra mappings/PK y la proyección prevista de las relaciones.
  Decisiones ausentes, cambios sin guardar o errores de vista previa bloquean construir.
- JSON, fkColumnName, joinTableName y tableName quedan en «Configuración avanzada».
- «Modelos lógicos generados» lista todos los resultados del modelo con versión,
  perfil capturado, status, checksum, fecha y «Ver resultado». Funciona para lectores
  autorizados; UUID solo queda en avanzado/debug.

«Guardar preparación» usa las APIs de perfil/mappings existentes, preserva claves
avanzadas y actualiza también el estado de validación V8. Los guardados son solicitudes
separadas (no una nueva operación batch atómica). Si un guardado falla, se refresca
configuración persistida, muestra error y se debe revisar/reintentar; no se construye
silenciosamente con una preparación parcialmente aplicada.

## API añadida, sin schema ni migraciones

- GET `/api/uml-models/{modelId}/logical-models`: listado read-only; exige permiso
  de lectura del proyecto. El nombre del perfil viene de profile_snapshot, no del
  perfil mutable; renombrarlo no cambia resultados históricos.
- POST `/api/model-versions/{versionId}/logical-models/preview` con
  `{"generationProfileId":"UUID"}`: ejecuta el mismo transformador V9 sin INSERT.
  Exige los mismos permisos de construcción; devuelve tablas propuestas e issues.

Build y preview comparten la captura de versión/perfil y el locking actual.
No se cambió LogicalModelTransformer, schema V9, checksum, renderer UML o generación.
La previa es informativa: construir sigue revalidando en Spring, incluyendo posibles
cambios concurrentes de configuración después de la previa.

## Checklist manual

- [ ] Abrir versión con Customer, Sale, Identifiable: se sugieren ENTITY/ENTITY/JAVA_INTERFACE.
- [ ] id únicos se preseleccionan; elegir otro identificador de la misma clase funciona.
- [ ] Clase sin id válido: mensaje con nombre exacto, construir deshabilitado.
- [ ] 1:N, N:1, N:M y composición muestran FK/join/cascade automáticos.
- [ ] 1:1 sin ownership claro pide propietario por nombre; seleccionarlo habilita guardar.
- [ ] Guardar preparación: vista previa V9 comprueba la configuración antes de habilitar construir.
- [ ] Guardar todos los campos del perfil; recargar y comprobar persistencia.
- [ ] Construir dos resultados y ver ambos en la lista después de F5, sin copiar UUID.
- [ ] Renombrar perfil y verificar que la lista conserva el nombre de perfil capturado.
- [ ] VIEWER/PUBLIC visitor solo consultan; no pueden guardar, previsualizar o construir.
- [ ] Diagramas, flechas, líneas, cardinalidades y manipulación permanecen iguales.

El checklist visual es manual, no una afirmación de prueba automatizada de navegador.

## Verificación

- Backend: 139 tests, sin fallos, errores ni tests omitidos.
- Web: 12 tests nativos Node (incluyen los existentes de labels), build y lint pasan.
- Sin nuevas dependencias, cambios de schema/migraciones, renderer, generación o commit.
