# CASEGen - Contrato de generación de backend Spring Boot

## 1. Propósito

Este documento define el contrato técnico que debe cumplir todo backend
Spring Boot generado por CASEGen a partir de un modelo UML validado.

El backend generado constituye un producto independiente de CASEGen.

CASEGen es responsable de:

1. validar el modelo UML;
2. crear una versión inmutable del modelo;
3. transformar el modelo UML en un modelo lógico relacional;
4. transformar el modelo lógico en un modelo de generación Spring Boot;
5. generar el código fuente y artefactos necesarios;
6. compilar el proyecto generado;
7. verificar su base de datos PostgreSQL;
8. ejecutar pruebas;
9. empaquetar el backend;
10. entregar al usuario un artefacto descargable.

Un backend no se considera generado correctamente únicamente porque
se hayan creado archivos Java.

La generación finaliza exitosamente solamente cuando el proyecto
generado supera las validaciones técnicas definidas en este documento.

---

## 2. Principios generales

### 2.1 Generación determinista

La generación de código debe ser determinista.

Para una misma combinación de:

- ModelVersion;
- LogicalModel;
- GenerationProfile;
- versión del generador CASEGen;

debe producirse estructuralmente el mismo backend.

La inteligencia artificial no será responsable de escribir directamente
el código Java final.

La IA podrá interpretar instrucciones del usuario y producir comandos
estructurados sobre el modelo.

El código final será producido mediante reglas, transformaciones y
plantillas deterministas.

---

### 2.2 Separación de responsabilidades

CASEGen mantendrá separadas las siguientes capas conceptuales:

```text
UML Semantic Model
        ↓
Validation
        ↓
ModelVersion
        ↓
Logical Model
        ↓
Spring Generation Model
        ↓
Templates + deterministic generator
        ↓
Generated Backend
        ↓
Compile + Tests + Verification
        ↓
ZIP artifact
```

The UML Semantic Model expresses the user's domain and is owned by CASEGen.
The Generated Backend is a derived, independent product. Generated Java,
Spring, JPA or database concepts must not be stored as attributes of UML
classifiers.

In particular, `uml_classifiers` must not contain `is_entity`, `is_service`,
`is_controller`, `is_repository` or `table_name`. Technology decisions belong
to `GenerationProfile` and `GenerationElementMapping`.

### 2.3 Immutable generation input

Every generation run must reference an immutable, validated `ModelVersion`.
The editable model is never a valid generation input. Changes made after a
version is created require validation and a new version before they can affect
another run.

A reproducibility fingerprint must identify at least:

- the `ModelVersion`;
- the `GenerationProfile` revision;
- the logical and Spring mapping rule versions;
- the template set version;
- the CASEGen generator version.

Operational metadata such as `generationId`, queue timestamps and storage keys
may differ between runs, but must not change the generated source content.

## 3. Intermediate models

### 3.1 LogicalModel

`LogicalModel` is a technology-neutral relational projection created from a
validated `ModelVersion` and the applicable mappings. It describes tables,
columns, primary keys, unique constraints, foreign keys, indexes and check
constraints without containing Java source code or JPA annotations.

It is a derived model. It must be traceable to the source UML elements and must
not mutate the semantic UML model.

### 3.2 GenerationProfile

`GenerationProfile` contains explicit target decisions and defaults, including:

- target Spring Boot, Java and PostgreSQL versions;
- base package and artifact coordinates;
- naming strategies;
- supported persistence and API conventions;
- dependency and plugin versions;
- optional output capabilities allowed by this contract.

A profile used by a successful run must be versioned or snapshotted so the run
can be reproduced later.

### 3.3 GenerationElementMapping

`GenerationElementMapping` associates semantic element UUIDs with generation
decisions. It is the proper location for decisions such as entity inclusion,
table/column naming, API exposure and relationship ownership.

Mappings must reference CASEGen UUIDs, not external XMI identifiers. Missing or
contradictory required mappings must produce validation issues instead of
guessing silently.

### 3.4 SpringGenerationModel

`SpringGenerationModel` is the fully resolved input to templates. It contains
the Java types, layers, endpoints, DTO shapes, mappings, validations, database
artifacts and tests to emit. Templates consume this model only; they do not
query the editable UML model or make architectural decisions.

## 4. UML-to-target mappings

The initial mapping contract is:

| UML concept | Logical/target projection | Rule |
|---|---|---|
| Package | Java package or bounded grouping | Determined by profile and explicit mapping. |
| Class | Entity/domain type when mapped for persistence | A UML class is not automatically a JPA entity. |
| Property | Field and, when persistent, column | Type, nullability, multiplicity and validation must be resolved. |
| Association/end | Relationship and optional foreign key | Ownership, cardinality and navigability must be explicit. |
| Generalization | Java inheritance only when supported by the profile | Unsupported strategies are validation errors. |
| Interface/realization | Java interface contract when supported | Never implies a Spring stereotype by itself. |
| Operation/parameter | Domain or application operation when mapped | Never implies a controller endpoint automatically. |

Mapping proceeds in two explicit transformations:

```text
ModelVersion + GenerationProfile + GenerationElementMapping
  -> LogicalModel
  -> SpringGenerationModel
```

Every derived element must preserve traceability to its semantic source or to a
documented profile default.

## 5. Contract of the generated Spring Boot backend

### 5.1 Baseline

The final ZIP must contain a complete, independently executable project with:

- Java 21 and a supported Spring Boot version fixed by the profile;
- Maven build descriptor and Maven Wrapper (`mvnw`, `mvnw.cmd` and `.mvn/`);
- Spring Web, Spring Data JPA and Bean Validation;
- PostgreSQL driver and Flyway;
- OpenAPI description;
- Docker Compose for local infrastructure;
- Postman collection/environment;
- automated tests and a README.

Dependency and plugin versions must be pinned or derived from a versioned
platform. A generation run must not silently select whatever version happens to
be latest.

### 5.2 Layered architecture

The generated project uses a layered architecture with clearly separated:

- `entity`: persistence representation and relationships;
- `repository`: Spring Data JPA persistence ports;
- `service`: transactions and business/application logic;
- `controller`: HTTP transport only, without business logic;
- `dto`: request and response contracts;
- `mapper`: deterministic conversion between DTOs and domain/entities;
- `exception`: domain/application errors and centralized HTTP handling.

Controllers must never expose JPA entities directly. Constructor injection is
required; generated field injection with `@Autowired` is forbidden.

### 5.3 Entities and repositories

Entities must have stable identifiers, explicit table/column mappings where
required, and relationships derived from the resolved logical model. Cascades,
fetch modes and ownership cannot be inferred solely from a visual UML arrow.

Repositories expose only the persistence operations required by generated
services. Generated controllers do not access repositories directly.

### 5.4 Services, controllers, DTOs and mappers

Services define transaction boundaries, enforce generated rules and translate
persistence failures into stable application errors. Controllers validate
requests, delegate once to a service and map responses/status codes.

Request and response DTOs are separate from entities. Mappers must be explicit
and deterministic; reflection-based behavior that changes output across
environments is not part of the baseline contract.

### 5.5 Validation and exception handling

UML multiplicity and explicit model constraints are projected to Bean
Validation only when their semantics are supported. Validation is applied at
API boundaries and reinforced by database constraints where appropriate.

The generated backend includes centralized exception handling with a stable
error representation for validation failures, missing resources, conflicts and
unexpected errors. It must not leak stack traces, SQL, credentials or internal
filesystem paths through HTTP responses.

## 6. PostgreSQL and schema management

Flyway migration SQL is the executable source of truth for the generated
schema. Hibernate must use:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

The generated application must not use Hibernate to create or update production
tables. A clean PostgreSQL database must be able to reach the expected schema by
applying the generated Flyway migrations in order.

The Docker Compose definition provides an isolated local PostgreSQL service and
uses configurable credentials. Real credentials and CASEGen's own database
credentials must never be embedded in the generated source or ZIP.

CASEGen's PostgreSQL database and a generated backend's PostgreSQL database are
different systems with separate schemas, credentials, migrations and lifecycle.

## 7. API and consumer artifacts

OpenAPI output must correspond to the generated controllers and DTOs. The
Postman collection must be derived from the same `SpringGenerationModel` so it
cannot drift through independent handwritten logic.

The README must explain prerequisites, environment variables, Maven Wrapper
commands, database startup, Flyway behavior, test execution and application
startup.

## 8. Generated tests

At minimum, the generated project includes tests appropriate to emitted code:

- unit tests for generated domain/service rules;
- controller tests for status codes and validation;
- persistence/integration tests for mappings and constraints;
- application startup/schema validation test;
- migration verification against a clean PostgreSQL instance.

Tests must not depend on CASEGen's development database or on another user's
generation workspace.

## 9. GenerationRun lifecycle

Each request creates a `GenerationRun` identified by a UUID `generationId`.
The allowed lifecycle is:

```text
QUEUED
  -> PREPARING
  -> GENERATING
  -> VERIFYING
  -> PACKAGING
  -> SUCCEEDED
```

`FAILED` and `CANCELLED` are terminal alternatives reachable from active
states. A failed run records a safe diagnostic summary and produces no
downloadable successful artifact. State transitions must be atomic and
auditable; retries create a new run or an explicitly linked attempt.

## 10. Isolated temporary workspace

Every run uses a dedicated workspace whose identity is derived from its UUID,
for example an implementation-controlled location equivalent to:

```text
generation-workspaces/<generationId>/backend/
```

During generation, `backend` is a normal directory: templates write files,
Maven compiles, tests run and verification inspects the result there.

After successful verification, `backend` is packaged as a downloadable ZIP.
The temporary workspace is then eligible for secure cleanup according to a
retention policy. It is never the source tree of CASEGen and generated files
must never be written under CASEGen modules or committed to the CASEGen
repository.

## 11. Verification gate

Packaging is permitted only after all applicable checks succeed:

1. validate the immutable semantic version and generation mappings;
2. construct and validate `LogicalModel`;
3. construct and validate `SpringGenerationModel`;
4. render the complete project into the isolated workspace;
5. reject unexpected paths, unresolved template markers and forbidden files;
6. apply Flyway migrations to an isolated clean PostgreSQL target;
7. start the Spring context with `ddl-auto=validate`;
8. compile and execute the generated Maven test lifecycle;
9. verify required OpenAPI, Postman, Docker Compose, Wrapper and README assets;
10. create the ZIP, calculate its size and cryptographic checksum;
11. publish it through `ArtifactStorage` and mark the run `SUCCEEDED`.

Writing source files alone is never success. Any failed mandatory check moves
the run to `FAILED`, preserves bounded diagnostics and prevents publication as a
successful artifact.

## 12. ArtifactStorage and ZIP delivery

`ArtifactStorage` is an application abstraction for storing and retrieving
generated artifacts. Its implementation may use protected local storage during
development and object storage in production without changing generation
semantics.

Artifact metadata must include the owning run, project/tenant context, media
type, byte size, checksum, creation time and retention/expiry information. A
storage key is internal and must not be accepted as arbitrary user input.

The ZIP is the only final downloadable backend artifact. It is not versioned
inside the CASEGen Git repository and is not stored in PostgreSQL as a large
binary. PostgreSQL stores run and artifact metadata; `ArtifactStorage` stores
the bytes.

## 13. Asynchronous and multiuser operation

Generation is asynchronous. The initial API request validates access and
returns an accepted run identifier; clients query or subscribe to run status and
request a download only after `SUCCEEDED`.

Multiple users may generate concurrently. Therefore:

- workspaces, build processes, databases and logs are isolated by
  `generationId`;
- authorization is checked using project membership for creation, status and
  download operations;
- queues enforce concurrency, time, memory, disk and process limits;
- one run cannot read, overwrite, cancel or download another user's artifacts;
- simultaneous runs never share mutable template or intermediate-model state.

## 14. Security requirements

The generation pipeline must:

- validate names before using them in packages, paths, SQL or Java identifiers;
- prevent path traversal, symlink escape and template injection;
- execute generated builds with bounded resources and without unnecessary
  network or host filesystem access;
- use approved, versioned templates and dependencies;
- keep secrets out of models, logs, generated files and ZIPs;
- authorize every run and artifact operation;
- retain checksums and audit information;
- clean temporary workspaces and expired artifacts safely.

User-supplied Java, SQL, shell fragments or template code are outside the
baseline contract and must not be executed implicitly.

## 15. Integration with Web, diagram editor, Flutter and AI

All clients converge on the same Semantic Core and application commands:

```text
Web / GLSP or React Flow / Voice / Flutter / AI
                    ↓
        validated semantic commands
                    ↓
              Semantic Core
                    ↓
   Validation -> ModelVersion -> Generation Pipeline
```

GLSP or React Flow is an editor adapter, not the owner of generation semantics.
Flutter, voice and chat use the same APIs and permissions. AI translates intent
into proposed structured semantic commands; it does not write final Java code,
mutate PostgreSQL directly or bypass validation/versioning.

## 16. Acceptance criteria

A generated backend is accepted only when:

- its input is an immutable validated `ModelVersion`;
- all decisions are traceable through versioned mappings and profiles;
- repeated generation produces equivalent source content;
- the generated project compiles and all mandatory tests pass;
- Flyway builds the schema and Hibernate validates it without mutation;
- the ZIP is complete, checksummed, authorized and downloadable;
- no generated backend source or ZIP is added to the CASEGen repository.
