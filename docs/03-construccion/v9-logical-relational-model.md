# V9 — Logical Relational Model

## API and source boundary

- POST `/api/model-versions/{versionId}/logical-models` with
  `{"generationProfileId":"UUID"}` → 201, status VALID or INVALID and issues.
- GET `/api/logical-models/{logicalModelId}` → full read-only result and frozen profile.
- GET `/api/logical-models/{logicalModelId}/tables` → tables/columns/keys/constraints.

OWNER, COLLABORATOR and SUPERUSER can build. VIEWER and authenticated PUBLIC
visitors can only read. PRIVATE outsiders cannot read or build. All endpoints
require authentication. There is no editable logical API and no Java/DDL generator.

Semantic input comes exclusively from ModelVersion.semantic_snapshot (schema 1).
The current profile plus its mappings are captured atomically under the same
model row lock used for existing profile mutations. This lock coordinates configuration;
no editable UML semantic rows participate in transformation. Archived model versions
can still be transformed. Existing results are unaffected by subsequent UML/profile changes.
Traceability UUIDs are not FK references to mutable UML rows.

Checksum uses canonical JSON, transformer version, semantic input, captured profile
and logical plan. It excludes logical row IDs, actor and timestamps. Same unchanged
Version/Profile input gives the same checksum; changing profile content may change it.
INVALID results retain actionable issues but no partial relational schema.

## Profile configuration

V9 supports ENTITY, JAVA_INTERFACE, IGNORE. Existing VALUE_OBJECT/SERVICE mappings
remain valid V8 concepts but produce UNSUPPORTED_MAPPING in V9. Every classifier
in the version needs an explicit mapping, including ignored classifiers.

ENTITY mapping config:

```json
{
  "idPropertyId": "UUID-of-property-in-version",
  "tableName": "customer",
  "properties": {
    "UUID-of-property": { "columnName": "email", "unique": true, "index": true }
  }
}
```

idPropertyId is mandatory (V8's implicit `id`/identifierPropertyId fallback is not
a V9 configuration). It must identify an owned/inherited required scalar String,
Integer or UnlimitedNatural property. All ENTITY tables have one identifier in MVP.
Inherited explicit IDs are supported by validation and transformation; multiple ENTITY inheritance is rejected.
Normal String/Integer/Boolean/Real/UnlimitedNatural properties map to
VARCHAR(255)/INTEGER/BOOLEAN/NUMERIC(19,2)/BIGINT, with nonnegative CHECK for UnlimitedNatural.
Multiplicity 1 is NOT NULL; 0..1 is nullable. Primitive collections are rejected.
Static/derived properties are skipped with warnings. Scalar classifier-typed properties
produce typed FK columns and require an ENTITY target. Association end properties
are handled only once by association transformation, not as ordinary attributes.

Profile config example for an ambiguous 1:1:

```json
{
  "inheritanceStrategy": "JOINED",
  "associations": {
    "UUID-of-association": {
      "fkOwnerClassifierId": "UUID-of-owning-entity",
      "fkColumnName": "address_id"
    }
  }
}
```

1:N places FK on N; 1:1 adds UNIQUE. Classifier-owned end metadata can identify the
1:1 owner; otherwise explicit fkOwnerClassifierId is required. Navigability alone
does not decide database ownership. Self 1:1 also needs referencedEndOrder (1/2).
N:M uses optional joinTableName/joinColumnA/joinColumnB and two ordered PK columns.
Composition forces FK part → whole with ON DELETE CASCADE; whole upper must be <=1.
Aggregation does not imply cascade. JOINED subclass PK is also FK to parent;
identifier SQL types must match. Realization and JAVA_INTERFACE do not create tables.
Associations involving IGNORE are skipped with warnings. Collection minimums are
not enforced by plain FK/join tables; finite collection maxima also require application-level
validation and produce warnings. Association upper=0 is rejected in this MVP.
Names default to deterministic snake_case; collisions require explicit configuration.
Table/column names are restricted to <=63 ASCII identifier characters.

## Web and manual checklist

The modeling validation panel now includes version/profile selectors,
«Construir modelo lógico», profile/mapping configuration and a result modal.
The modal shows table/column/PK/FK counts, UNIQUE/CHECK/indexes, traceability issues
and logical ID/checksum. Results cannot be manually edited. Read-only users can
open a persisted result by its logical model ID; Spring rechecks project access.
Missing profile/version
disables construction with an explanatory selection placeholder.

- [ ] Create Customer and Sale, each with required String id, and 1 ↔ 0..* association.
- [ ] Validate/create version; configure ENTITY mappings with explicit idPropertyId.
- [ ] Build and inspect customer/sale tables, required PK and customer FK in Sale.
- [ ] Repeat build: same checksum, distinct logical model IDs.
- [ ] Configure a nullable primitive property: inspect nullable=true.
- [ ] Build ambiguous 1:1: INVALID with AMBIGUOUS_ONE_TO_ONE; set owner then verify UNIQUE.
- [ ] Build Student/Course N:M: join table with two FK and ordered composite PK.
- [ ] Build Order/OrderItem composition: FK in part and ON DELETE CASCADE.
- [ ] Build Person/Customer JOINED: subclass PK also references parent.
- [ ] Map Interface as JAVA_INTERFACE: no corresponding table.
- [ ] Remove idPropertyId or add primitive collection: INVALID, issues and zero tables.
- [ ] Edit mutable UML after version creation: prior version/results remain unchanged.
- [ ] VIEWER/PUBLIC visitor cannot build; COLLABORATOR can.
- [ ] Existing diagram routes, straight edges, endpoints, markers and labels are unchanged.

These browser checks are a manual checklist, not a claim of automated visual testing.

## Verification

- Backend: 137 tests, 0 failures/errors/skips, including 15 transformer tests and
  6 PostgreSQL/API integration tests for V9.
- Web: `npm run build` and `npm run lint` pass. Existing large-bundle warning
  remains non-blocking.
- Flyway V9 applied successfully against the configured local PostgreSQL.
- No changes to diagram renderers, edge geometry, markers or anchor interaction.
- No dependencies added, code generation, V10 implementation or commit.
