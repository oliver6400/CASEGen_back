# CASEGen - Engineering Instructions

## Project

CASEGen is a collaborative CASE tool for UML modeling and
Spring Boot backend generation.

## Architecture

Use a modular monolith.

Main modules:

- project
- modeling
- validation
- versioning
- generation
- collaboration
- synchronization
- auth
- shared

## Backend

Technology:

- Java 21
- Spring Boot
- Maven
- PostgreSQL
- Spring Data JPA
- Bean Validation
- Flyway

## Coding rules

- Package names must be in English.
- Class names must be in English.
- Use UUID for aggregate identifiers.
- Controllers must not contain business logic.
- Business logic belongs in services/domain.
- Never expose JPA entities directly from controllers.
- Use request and response DTOs.
- Use constructor injection.
- Do not use field injection with @Autowired.
- Validate input.
- Add tests for domain rules.
- Do not introduce dependencies without explaining why.

## Modeling terminology

Use:

- Project
- Diagram
- ModelClass
- ModelAttribute
- ModelRelationship
- ModelVersion

Do not use the name "Class" for the UML model entity.

## Generation

Code generation must be deterministic.

Never use an LLM to directly generate the final Spring Boot project.

Generation flow:

ModelVersion
-> GenerationModel
-> Templates
-> Source Code

Generate only from immutable validated versions.

## Git

Keep changes small.
Do not modify unrelated files.
Run tests before considering a task complete.