# CASEGen Database Rules

## General rules

- PostgreSQL 17.
- Persistent identifiers use UUID.
- Java target is Java 21.
- Database changes are managed only through Flyway.
- Hibernate `ddl-auto` remains `validate`.
- Existing Flyway migrations must never be modified after application.
- Timestamps use PostgreSQL `TIMESTAMPTZ`.
- Database names use `snake_case`.
- Java names use standard Java camelCase/PascalCase.

## Enum strategy

PostgreSQL native ENUM types are not used for the MVP.

Categorical fields use VARCHAR plus CHECK constraints.

This allows categories to evolve through future migrations
without tightly coupling the schema to PostgreSQL ENUM types.

## Email identity

User email addresses are unique case-insensitively. Flyway creates one explicit
functional unique index on `lower(email)` for `users`; a second case-sensitive
unique constraint is intentionally not created because it would be redundant.

## Foreign keys

Foreign keys must have explicit constraint names.

Deletion behavior must be explicitly selected.

Do not use cascade delete unless the aggregate semantics
clearly require it.

## XMI

Enterprise Architect XMI identifiers are external identifiers.

They must never be used as CASEGen primary keys.

CASEGen uses internal UUID identifiers.

## Semantic model

The semantic UML model is independent of diagram layout.

Moving or deleting a graphical representation must not
automatically delete the semantic UML element.

## Versioning

Model versions are immutable snapshots.

Code generation is performed from a validated model version,
not directly from the editable model.

## AI

AI and mobile clients never directly modify database tables.

They generate semantic commands that are validated and
executed through the CASEGen application layer.
