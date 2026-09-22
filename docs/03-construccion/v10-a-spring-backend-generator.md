# V10-A — Spring Boot Backend Generator

## Flujo

`LogicalModel VALID -> GenerationRun -> SpringGenerationModel -> workspace aislado -> Maven compile -> Maven test -> PostgreSQL/Flyway/JPA validate -> ZIP`.

La entrada no consulta `UmlModel` editable. Cada ejecución usa
`runtime/generation/{generationId}/workspace` y un esquema PostgreSQL temporal
`generation_{generationId}` que se elimina al terminar. El ZIP solo se publica
cuando las verificaciones terminan correctamente.

## Checklist manual

1. Reiniciar CASEGen para aplicar V10.
2. Abrir un LogicalModel con estado `VALID`.
3. Pulsar **Generar backend**.
4. Comprobar las etapas `MAVEN_COMPILE`, `MAVEN_TEST` y
   `FLYWAY_JPA_VALIDATE`.
5. Confirmar `SUCCESS`, un `backend.zip` y su checksum.
6. Provocar un perfil inválido y comprobar que no permite generar.
7. Probar con OWNER, COLLABORATOR y VIEWER; VIEWER debe recibir 403.
8. Generar dos veces y confirmar IDs y carpetas diferentes.
9. Abrir el ZIP y verificar `pom.xml`, Wrapper, código por capas,
   `application.yml`, migración Flyway, Compose, `.env.example` y README.

El editor UML, su geometría, líneas y cardinalidades no forman parte de este
incremento y no fueron modificados.

## V10-A.1: ejecución observable

El `POST /api/logical-models/{logicalModelId}/generations` persiste una ejecución
`QUEUED`, responde `202 Accepted` con `generationId` y entrega el pipeline a un
ejecutor dedicado. El cliente consulta `GET /api/generations/{generationId}`
hasta `SUCCESS` o `FAILED`.

Cada transición queda persistida y cada etapa conserva resultado y log:
`GENERATING`, `COMPILING`, `TESTING`, `VERIFYING_DATABASE` y `PACKAGING`.
El historial se recupera, newest first, mediante
`GET /api/logical-models/{logicalModelId}/generations`; por ello sobrevive a un
refresco del navegador. Cada ejecución mantiene su propio directorio
`runtime/generation/{generationId}` y solo una ejecución que completa
`PACKAGING` registra `backend.zip`.
