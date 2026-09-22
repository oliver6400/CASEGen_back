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

### V5 - UML Features

Migration: `V5__create_uml_features.sql`
Status: implemented and verified against PostgreSQL 17.

Tables:

- `uml_properties` with primitive/classifier typing and multiplicity;
- `uml_operations` without a duplicated return type;
- `uml_parameters` with direction, ordered positions and a maximum of one
  `RETURN` parameter per operation.

All feature mutations create the root `uml_elements` row and specialization in
one transaction and increment the semantic model revision exactly once.

### V6 - UML Class Relationships

Migration: `V6__create_uml_relationships.sql`
Status: implemented and verified against PostgreSQL 17.

Changes:

- nullable `uml_properties.owner_classifier_id` for association-owned ends;
- binary `uml_associations` with exactly two property-based ends created atomically;
- navigability, aggregation, composition, end ownership and multiplicity;
- acyclic classifier generalizations;
- realizations whose supplier must be an Interface.

## Planned

The exact table definitions, constraints and migration filenames remain subject
to review when each increment begins.

### V7 - Class Diagram persistence

Migration: `V7__create_class_diagram_model.sql`
Status: implemented and verified against PostgreSQL 17.

- class diagrams are projections of one UML model;
- nodes persist element placement, size and z-index;
- edges reference existing semantic UML relationships and diagram nodes;
- notation changes advance `Diagram.revision` without changing `UmlModel.revision`.

V7.1 adds notation-only edge routing metadata (`source_side`, `target_side`,
distributed anchor indexes and JSON waypoints). It does not add UML semantics
and therefore remains compatible with XMI import/export.

### V8 - Validation, ModelVersion and GenerationProfile

- synchronous semantic/generation validation runs and issues;
- immutable model versions;
- generation profiles and element mappings.

Generation decisions belong to profiles/mappings, never to
`uml_classifiers`.

Migration: `V8__create_validation_versions_and_generation_profiles.sql`.
Validation records model revision and generation configuration checksum; versions
require the latest VALID, current run under the existing model lock. Snapshots
contain all V3/V5/V6 semantic tables and generation configuration; diagram data
is captured separately. Model versions are append-only, enforced by a trigger.
No UML revision changes for validation, version creation or profile editing.
Profile/mapping changes invalidate validations through their configuration hash.

### V8.1 - Project Sharing

Migration: `V8_1__add_project_sharing.sql`. Existing projects remain PRIVATE.
PUBLIC permits authenticated read access only. Invitations reuse V2 tables,
with REJECTED added to the status constraint. Acceptance requires the signed-in
recipient email, a pending unexpired invitation, and a project lock. OWNER cannot
be altered/revoked through member management. No SMTP or generation implementation.

### V9 - Logical Relational Model

- logical models;
- logical tables and columns;
- primary/unique/foreign keys;
- indexes and check constraints;
- traceability to semantic elements and generation mappings.

Migration: `V9__create_logical_relational_model.sql`. Construction uses an immutable
ModelVersion semantic snapshot and a captured GenerationProfile/mappings snapshot.
No reads from editable UML tables for transformation. Invalid configurations persist
issues and zero tables; VALID results persist ordered PK/FK/UNIQUE/INDEX columns.
Sources deliberately retain UML UUIDs without live foreign keys. SINGLE-parent
JOINED is the MVP inheritance strategy. No manual logical edits or code generation.
Profile deletion is restricted while derived logical models reference it.

### V9.1 - Validation creation order

Migration: `V9_1__add_validation_run_order.sql`.

Adds a unique sequence-backed `validation_runs.run_order`. Latest validation is
selected by creation order, not `started_at`, so historical future timestamps
cannot replace a newly completed validation after refetch or version creation.
Historical rows are backfilled by model, semantic revision, timestamp and UUID;
timestamps, issues and immutable ModelVersion snapshots remain unchanged.
This patch does not change V9 transformation or diagram geometry.

### V10 - Spring Boot backend generation

Migration: `V10__create_generation_pipeline.sql`.

Persists generation lifecycle, bounded verification logs and ZIP artifact metadata.
Every run is traceable to a VALID LogicalModel, immutable ModelVersion, frozen
GenerationProfile and requester. Source workspaces and ZIP bytes live below the
configured runtime storage and are excluded from Git.

### V11 - Independent Normalization Analysis

Migration: `V11__create_normalization_analysis.sql`.

Persists deterministic 1NF/2NF/3NF analysis runs and explanatory issues derived
only from a persisted LogicalModel. Issues retain logical table/column references
and UML traceability in metadata when available. Normalization never changes UML
validation, ModelVersion, LogicalModel status/checksum or GenerationRun readiness;
PASS, WARNING, FAIL and UNKNOWN are advisory results independent from V10.

### V12 - Realtime collaboration activity

Migration: `V12__create_collaboration_activity.sql`.

Persists only important model/diagram changes confirmed after transaction commit.
Online presence, selection and editing indicators remain ephemeral and are never
stored in the UML or Diagram models.

### Future - Generation Pipeline (number to be assigned)

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

### Future - Diagram enhancements

- waypoints and persisted handle/routing choices;
- additional presentation metadata beyond V7.

### Future - UML Sequence Model

- interactions;
- lifelines;
- occurrence specifications;
- synchronous call and reply messages.

### Interoperability (implemented without persistence migration)

- file asset metadata;
- UML/XMI imports and exports;
- Enterprise Architect interoperability traceability and diagnostics.

### Collaboration History

- semantic and diagram activity history implemented by V12;
- future audit/synchronization metadata remains deferred;
- Yjs is not an authoritative database.

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
V5  UML Features                              implemented
V6  UML Relationships                         implemented
V7  Class Diagram persistence                 implemented
Future increments retain the planned order documented above; their migration
numbers must be reassigned before implementation.
```
