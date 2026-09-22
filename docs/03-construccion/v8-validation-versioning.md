# V8 — Validation, ModelVersion y GenerationProfile

## Alcance

Validación síncrona, versiones inmutables y configuración declarativa de generación.
No contiene LogicalModel, templates, proyectos generados ni botón Generar Backend.
OWNER/COLLABORATOR y SUPERUSER pueden mutar; VIEWER solo puede consultar.
Las operaciones V8 no incrementan UmlModel.revision ni Diagram.revision.

La persistencia V8 utiliza JdbcTemplate ya disponible mediante el stack JPA, sin
dependencias adicionales. El Semantic Core mantiene JPA y su bloqueo de UmlModel.
Snapshots JSONB normalizados por PostgreSQL tienen schemaVersion=1, orden estable
por UUID, sin timestamps volátiles. SHA-256 cubre el snapshot semántico y perfiles;
no incluye layout, capturado separadamente y completo en una consulta MVCC.
Cada versión referencia el run que la aprobó. Un trigger rechaza UPDATE/DELETE.

## API

- POST `/api/uml-models/{modelId}/validations`
- GET `/api/validations/{validationId}`
- GET `/api/uml-models/{modelId}/validations/latest` (404 cuando no existe)
- POST/GET `/api/uml-models/{modelId}/versions`
- POST/GET `/api/uml-models/{modelId}/generation-profiles`
- GET/PUT/DELETE `/api/uml-models/{modelId}/generation-profiles/{profileId}`
- POST/GET `/api/uml-models/{modelId}/generation-profiles/{profileId}/mappings`
- GET/PUT/DELETE `/api/uml-models/{modelId}/generation-profiles/{profileId}/mappings/{mappingId}`

Perfil de ejemplo:

```json
{"name":"Backend","target":"SPRING_BOOT","groupId":"com.example","artifactId":"customer-api","basePackage":"com.example.customer","javaVersion":21,"springBootVersion":"4.1.1","database":"POSTGRESQL","configJson":{}}
```

Mapping de ejemplo (UUIDs del modelo real):

```json
{"umlElementId":"UUID_CLASSIFIER","targetKind":"ENTITY","configJson":{"identifierPropertyId":"UUID_PROPERTY_ID"}}
```

JAVA_INTERFACE requiere INTERFACE; ENTITY/VALUE_OBJECT/SERVICE requieren CLASS.
IGNORE acepta ambos. ENTITY requiere un identificador String, Integer o
UnlimitedNatural obligatorio 1..1, no static/derived; se selecciona por
identifierPropertyId o por el atributo llamado id. Primitivos se consideran
mapeables a String/text, Integer/integer, Boolean/boolean, Real/double precision
y BigInteger/numeric. Properties classifier requieren ENTITY/VALUE_OBJECT en
el mismo perfil; parameters classifier requieren un mapping Java no IGNORE.

Sin perfil se emite WARNING y se permite una versión semántica. ERROR impide
versionar. Cambiar el modelo o perfiles/mappings deja obsoleta la validación.
Crear/eliminar/mover una representación gráfica no invalida validación semántica.

Validar siempre crea una ValidationRun nueva para la revisión actual bajo lock;
las validaciones/versiones históricas no bloquean revalidación. Cada revisión
semántica puede versionarse una vez mediante la API. Un intento repetido devuelve
409 MODEL_REVISION_ALREADY_VERSIONED con el mensaje «La revisión rN ya está
versionada como vM». Se conservan posibles duplicados históricos, sin borrarlos.
Una revisión nueva requiere validación VALID actual antes de crear la siguiente
versión. V9.1 muestra versión seleccionada y revisión capturada; sus Classifiers
se obtienen exclusivamente del snapshot, no del UML editable.

## Checklist Web

- [ ] Validar modelo: mostrar status y issues con severity/code/message.
- [ ] Localizar un issue: seleccionar su elemento semántico (o asociación del end).
- [ ] Crear versión permanece deshabilitado sin validación actual VALID.
- [ ] Crear v1/v2; verificar números y mensajes de versión inmutable.
- [ ] Editar Class/Property: validación obsoleta y Crear versión deshabilitado.
- [ ] F5 conserva acceso a última validación y lista de versiones.
- [ ] VIEWER consulta resultados, pero no muestra acciones de mutación.
- [ ] Verificar que navegación de diagramas, layout y renderer V7.2 no cambiaron.
