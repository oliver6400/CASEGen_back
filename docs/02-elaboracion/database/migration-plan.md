# CASEGen Flyway Migration Plan

This document records the intended migration sequence. Planned entries define
scope only: no future migration exists until its SQL is implemented and
reviewed. Applied migrations must never be modified.

## Implemented

### V1 - Projects

Migration: `V1__create_projects_table.sql`  
Status: implemented; must not be modified.

Tables:

- `projects`

### V2 - Users and Project Collaboration

Migration: `V2__create_users_and_project_collaboration.sql`  
Status: implemented; must not be modified after application.

Tables:

- `users`
- `project_members`
- `project_invitations`

### V3 - UML Model Core

Migration: `V3__create_uml_model_core.sql`
Status: implemented and verified against PostgreSQL 17.

Tables:

- `uml_models`
- `uml_elements`
- `uml_packages`
- `uml_classifiers`

The semantic revision and JPA optimistic lock version are separate. Internal
UUID identity and external XMI identity remain separate. No Spring/JPA target
flags are stored in UML classifiers.

### V4 - Security and Project Authorization

Migration: `V4__add_security_and_refine_roles.sql`
Status: implemented and verified against PostgreSQL 17.

Changes:

- `users.system_role`: `USER`, `SUPERUSER`;
- `project_members.role`: `OWNER`, `COLLABORATOR`, `VIEWER`;
- `auth_refresh_tokens` for hashed, expirable and revocable refresh tokens;
- at most one active `OWNER` per project through a partial unique index.

### V4.1 - Superuser Bootstrap

Migration: `V4_1__add_superuser_bootstrap.sql`
Status: implemented and verified against PostgreSQL 17.

Changes:

- singleton `system_bootstrap` row for installation bootstrap state;
- initialization from an existing `SUPERUSER`, without deleting or changing users;
- partial unique index that enforces at most one global `SUPERUSER`;
- transactional row locking makes the first registration the `SUPERUSER` and all
  subsequent registrations `USER`.

## Planned

The exact table definitions, constraints and migration filenames remain subject
to review when each increment begins.

### V5 - UML Features

- properties;
- operations;
- parameters;
- supported primitive types, visibility, direction and multiplicity details.

### V6 - UML Relationships

- associations and association ends;
- generalizations;
- realizations;
- ownership, multiplicity and navigability references.

### V7 - Validation, Rules, Versioning and Generation Profile

- validation rules, runs and issues;
- immutable model versions;
- generation profiles and element mappings.

Generation decisions belong to profiles/mappings, never to
`uml_classifiers`.

Candidate concepts for later DBML review:

- `generation_profiles`
- `generation_element_mappings`

### V8 - Logical Relational Model

- logical models;
- logical tables and columns;
- primary/unique/foreign keys;
- indexes and check constraints;
- traceability to semantic elements and generation mappings.

### V9 - Generation Pipeline

- asynchronous generation runs;
- artifact metadata;
- verification/test runs;
- state transitions, diagnostics, checksums and retention metadata.

Candidate concepts for later DBML review:

- `generation_runs`
- `generation_artifacts`
- `generation_test_runs`

Artifact bytes and generated ZIPs are stored through `ArtifactStorage`, not as
large binaries in PostgreSQL and not in the CASEGen Git repository.

### V10 - Diagram Model

- diagrams;
- nodes and edges;
- positions, dimensions, routing and presentation metadata separated from UML
  semantics.

### V11 - UML Sequence Model

- interactions;
- lifelines;
- occurrence specifications;
- synchronous call and reply messages.

### V12 - Interoperability

- file asset metadata;
- UML/XMI imports and exports;
- Enterprise Architect interoperability traceability and diagnostics.

### V13 - Collaboration History

- semantic change history/events;
- collaboration metadata required for audit and synchronization;
- version-safe references without making Yjs the authoritative database.

### V14 - Assistant and Mobile Sync

- client devices and synchronization cursors;
- assistant sessions, messages and proposed semantic commands;
- mobile/voice/AI integration through the application layer.

## Required order

```text
V1  Projects                                  implemented
V2  Users + Project Collaboration             implemented
V3  UML Model Core                            implemented
V4  Security + Project Authorization           implemented
V4.1 Superuser Bootstrap                       implemented
V5  UML Features                              planned
V6  UML Relationships                         planned
V7  Validation + Rules + Versioning
    + Generation Profile                      planned
V8  Logical Relational Model                  planned
V9  Generation Pipeline                       planned
V10 Diagram Model                             planned
V11 UML Sequence Model                        planned
V12 Interoperability                          planned
V13 Collaboration History                     planned
V14 Assistant and Mobile Sync                 planned
```
