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

## Planned

The exact table definitions, constraints and migration filenames remain subject
to review when each increment begins.

### V3 - UML Model Core

- UML models, base elements, packages and classifiers.
- Internal UUID identity and external XMI identity remain separate.
- No Spring, JPA, controller, repository or physical table flags are added to
  UML classifiers.

Candidate tables already represented in the DBML:

- `uml_models`
- `uml_elements`
- `uml_packages`
- `uml_classifiers`

### V4 - UML Features

- properties;
- operations;
- parameters;
- supported primitive types, visibility, direction and multiplicity details.

### V5 - UML Relationships

- associations and association ends;
- generalizations;
- realizations;
- ownership, multiplicity and navigability references.

### V6 - Validation, Rules, Versioning and Generation Profile

- validation rules, runs and issues;
- immutable model versions;
- generation profiles and element mappings.

Generation decisions belong to profiles/mappings, never to
`uml_classifiers`.

Candidate concepts for later DBML review:

- `generation_profiles`
- `generation_element_mappings`

### V7 - Logical Relational Model

- logical models;
- logical tables and columns;
- primary/unique/foreign keys;
- indexes and check constraints;
- traceability to semantic elements and generation mappings.

### V8 - Generation Pipeline

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

### V9 - Diagram Model

- diagrams;
- nodes and edges;
- positions, dimensions, routing and presentation metadata separated from UML
  semantics.

### V10 - UML Sequence Model

- interactions;
- lifelines;
- occurrence specifications;
- synchronous call and reply messages.

### V11 - Interoperability

- file asset metadata;
- UML/XMI imports and exports;
- Enterprise Architect interoperability traceability and diagnostics.

### V12 - Collaboration History

- semantic change history/events;
- collaboration metadata required for audit and synchronization;
- version-safe references without making Yjs the authoritative database.

### V13 - Assistant and Mobile Sync

- client devices and synchronization cursors;
- assistant sessions, messages and proposed semantic commands;
- mobile/voice/AI integration through the application layer.

## Required order

```text
V1  Projects                                  implemented
V2  Users + Project Collaboration             implemented
V3  UML Model Core                            planned
V4  UML Features                              planned
V5  UML Relationships                         planned
V6  Validation + Rules + Versioning
    + Generation Profile                      planned
V7  Logical Relational Model                  planned
V8  Generation Pipeline                       planned
V9  Diagram Model                             planned
V10 UML Sequence Model                        planned
V11 Interoperability                          planned
V12 Collaboration History                     planned
V13 Assistant and Mobile Sync                 planned
```
