package com.casegen.generation.backend.service;

import com.casegen.generation.logical.dto.LogicalModelDtos.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Deterministic renderer that reads only the persisted LogicalModel. */
@Component
public class SpringBackendRenderer {
    public record Traceability(UUID projectId, UUID modelId, String modelName, UUID modelVersionId,
            long modelVersionNumber, UUID logicalModelId) {}

    public void render(Path root, Response in, Traceability trace) throws IOException {
        SpringGenerationModel model = new SpringGenerationModel(in.profileSnapshot().path("profile"), in.tables());
        JsonNode p = model.profile();
        String base = p.path("basePackage").asText();
        String artifact = p.path("artifactId").asText();
        String app = javaName(artifact) + "Application";
        String pkg = "src/main/java/" + base.replace('.', '/');

        put(root, "pom.xml", pom(p)
                .replace("<dependencies>", "<dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway</artifactId></dependency>")
                .replace("<testcontainers.version>2.0.3</testcontainers.version>", "")
                .replace("<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-postgresql</artifactId><version>$" + "{testcontainers.version}</version><scope>test</scope></dependency>", ""));
        copyWrapper(root);
        put(root, pkg + "/" + app + ".java", "package " + base + ";\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n@SpringBootApplication\npublic class " + app + " {\n    public static void main(String[] a) {\n        SpringApplication.run(" + app + ".class, a);\n    }\n}\n");
        put(root, pkg + "/exception/ResourceNotFoundException.java", "package " + base + ".exception;\npublic class ResourceNotFoundException extends RuntimeException {\n    public ResourceNotFoundException(String m) {\n        super(m);\n    }\n}\n");
        put(root, pkg + "/exception/GlobalExceptionHandler.java", handler(base));

        for (Table t : model.generatedEntities()) {
            renderTable(root, pkg, base, model, t);
        }

        put(root, "src/main/resources/application.yml", applicationYaml());
        put(root, "src/main/resources/db/migration/V1__create_schema.sql", authSchema() + schema(model.tables()));
        put(root, "src/main/resources/db/migration/V2__seed_test_data.sql", generateSeedDataSql(model.generatedEntities()));
        put(root, pkg + "/auth/User.java", generatedAuthEntity(base));
        put(root, pkg + "/auth/UserRepository.java", generatedUserRepository(base));
        put(root, pkg + "/auth/RegisterRequest.java", generatedRegisterRequest(base));
        put(root, pkg + "/auth/LoginRequest.java", generatedLoginRequest(base));
        put(root, pkg + "/auth/AuthResponse.java", generatedAuthResponse(base));
        put(root, pkg + "/auth/AuthService.java", generatedAuthService(base));
        put(root, pkg + "/auth/AuthController.java", generatedAuthController(base));
        put(root, pkg + "/auth/SecurityConfig.java", generatedSecurityConfig(base));

        put(root, pkg + "/ai/VoiceCommandRequest.java", generatedVoiceRequest(base));
        put(root, pkg + "/ai/VoiceCommandResponse.java", generatedVoiceResponse(base));
        put(root, pkg + "/ai/VoiceAiService.java", generatedVoiceService(base, model.generatedEntities()));
        put(root, pkg + "/ai/VoiceAiController.java", generatedVoiceController(base));

        put(root, pkg + "/sync/SyncMutationItem.java", generatedSyncMutationItem(base));
        put(root, pkg + "/sync/SyncMutationResult.java", generatedSyncMutationResult(base));
        put(root, pkg + "/sync/SyncBatchRequest.java", generatedSyncBatchRequest(base));
        put(root, pkg + "/sync/SyncBatchResponse.java", generatedSyncBatchResponse(base));
        put(root, pkg + "/sync/SyncService.java", generatedSyncService(base, model.generatedEntities()));
        put(root, pkg + "/sync/SyncController.java", generatedSyncController(base));

        put(root, "src/test/java/" + base.replace('.', '/') + "/ApplicationContextTest.java", contextTest(base));
        String database = databaseName(artifact), databaseUser = database + "_user", databasePassword = developmentPassword(artifact);
        put(root, ".env", devEnvironment(database, databaseUser, databasePassword));
        put(root, ".env.example", exampleEnvironment(database, databaseUser));
        put(root, ".gitignore", ".env\ntarget/\n*.log\n.idea/\n.vscode/\nmobile/node_modules/\nmobile/.expo/\n");
        put(root, "run.ps1", runPowerShell());
        put(root, "run.sh", runShell());
        put(root, "start-all.ps1", startAllPowerShell());
        put(root, "start-all.sh", startAllShell());
        put(root, "fast-start.ps1", fastStartPowerShell());
        put(root, "fast-start.sh", fastStartShell());
        put(root, "Dockerfile", dockerfile(p.path("javaVersion").asInt()));
        put(root, "docker-compose.yml", compose());
        put(root, "README.md", readme(artifact, database, trace));
        put(root, "postman/" + artifact + ".postman_collection.json", postman(artifact, model.generatedEntities()));
    }

    private void renderTable(Path root, String pkg, String b, SpringGenerationModel model, Table t) throws IOException {
        String n = javaName(t.name()), r = t.name().replace('_', '-');
        if (hasCompositeId(t)) put(root, pkg + "/entity/" + n + "Id.java", embeddedId(b, n, t));
        put(root, pkg + "/entity/" + n + ".java", entity(b, n, model, t));
        put(root, pkg + "/dto/" + n + "Request.java", request(b, n, t));
        put(root, pkg + "/dto/" + n + "Response.java", response(b, n, t));
        put(root, pkg + "/repository/" + n + "Repository.java", repository(b, n, t));
        put(root, pkg + "/mapper/" + n + "Mapper.java", mapper(b, n, t));
        put(root, pkg + "/service/" + n + "Service.java", service(b, n, t));
        put(root, pkg + "/controller/" + n + "Controller.java", controller(b, n, r, t));
    }

    private String entity(String b, String n, SpringGenerationModel model, Table t) {
        Set<String> pk = new LinkedHashSet<>(t.primaryKey().columns());
        boolean composite = hasCompositeId(t);
        StringBuilder s = new StringBuilder("package " + b + ".entity;\nimport jakarta.persistence.*;\nimport java.util.*;\n@Entity @Table(name=\"")
                .append(t.name()).append("\") public class ").append(n).append(" {\npublic ").append(n).append("() {}\n");
        if (composite) {
            s.append("@EmbeddedId private ").append(n).append("Id id = new ").append(n).append("Id();\n");
        }
        for (Column c : t.columns()) {
            if (composite && pk.contains(c.name())) continue;
            if (pk.contains(c.name())) s.append("@Id ");
            s.append("@Column(name=\"").append(c.name()).append("\",nullable=").append(c.nullable()).append(columnOptions(c.sqlType())).append(") private ").append(javaType(c.sqlType())).append(' ').append(field(c.name())).append(";\n");
        }
        int relationIndex = 0;
        for (ForeignKey fk : t.foreignKeys()) {
            if (fk.columns().size() != 1) continue;
            String column = fk.columns().getFirst().column(), target = javaName(fk.referencedTable()), relation = field(fk.referencedTable()) + (++relationIndex);
            boolean nullable = t.columns().stream().filter(c -> c.name().equals(column)).findFirst().orElseThrow().nullable();
            boolean unique = t.uniqueConstraints().stream().anyMatch(k -> k.columns().equals(fk.columns().stream().map(ColumnPair::column).toList()));
            s.append(unique ? "@OneToOne" : "@ManyToOne").append("(fetch=FetchType.LAZY,optional=").append(nullable).append(") @JoinColumn(name=\"").append(column).append("\",insertable=false,updatable=false,nullable=").append(nullable).append(") private ").append(target).append(' ').append(relation).append(";\n");
        }
        for (SpringGenerationModel.ManyToMany relation : model.relationshipsFor(t.name())) {
            appendManyToMany(s, t.name(), relation);
        }
        if (!composite) {
            Column idCol = id(t);
            String idField = field(idCol.name());
            String idJavaType = javaType(idCol.sqlType());
            if ("java.util.UUID".equals(idJavaType)) {
                s.append("@PrePersist void ensureId() { if (this.").append(idField).append(" == null) this.").append(idField).append(" = java.util.UUID.randomUUID(); }\n");
            } else if ("String".equals(idJavaType)) {
                s.append("@PrePersist void ensureId() { if (this.").append(idField).append(" == null || this.").append(idField).append(".isBlank()) this.").append(idField).append(" = java.util.UUID.randomUUID().toString(); }\n");
            }
        }
        if (composite) {
            s.append("public ").append(n).append("Id getId() { return id; } public void setId(").append(n).append("Id id) { this.id = id; }\n");
        }
        for (Column c : t.columns()) {
            String f = field(c.name()), cap = cap(f), jt = javaType(c.sqlType());
            if (composite && pk.contains(c.name())) {
                s.append("public ").append(jt).append(" get").append(cap).append("() { return id == null ? null : id.get").append(cap).append("(); } public void set").append(cap).append('(').append(jt).append(' ').append(f).append(") { if (id == null) id = new ").append(n).append("Id(); id.set").append(cap).append('(').append(f).append("); }\n");
            } else {
                s.append("public ").append(jt).append(" get").append(cap).append("() { return ").append(f).append("; } public void set").append(cap).append('(').append(jt).append(' ').append(f).append(") { this.").append(f).append('=').append(f).append("; }\n");
            }
        }
        return s.append("}\n").toString();
    }

    private String embeddedId(String b, String n, Table t) {
        StringBuilder s = new StringBuilder("package " + b + ".entity;\nimport jakarta.persistence.*;\nimport java.io.Serializable;\nimport java.util.Objects;\n@Embeddable public class ").append(n).append("Id implements Serializable {\npublic ").append(n).append("Id() {}\n");
        for (String name : t.primaryKey().columns()) {
            Column c = column(t, name);
            s.append("@Column(name=\"").append(c.name()).append("\",nullable=false").append(columnOptions(c.sqlType())).append(") private ").append(javaType(c.sqlType())).append(' ').append(field(c.name())).append(";\n");
        }
        for (String name : t.primaryKey().columns()) {
            Column c = column(t, name);
            String f = field(c.name()), cap = cap(f), type = javaType(c.sqlType());
            s.append("public ").append(type).append(" get").append(cap).append("() { return ").append(f).append("; } public void set").append(cap).append('(').append(type).append(' ').append(f).append(") { this.").append(f).append('=').append(f).append("; }\n");
        }
        s.append("public static ").append(n).append("Id valueOf(String raw) {\n    String[] parts = raw.split(\":\", -1);\n    ").append(n).append("Id result = new ").append(n).append("Id();\n");
        int idx = 0;
        for (String name : t.primaryKey().columns()) {
            Column c = column(t, name);
            String cap = cap(field(c.name()));
            String jt = javaType(c.sqlType());
            s.append("    if (parts.length > ").append(idx).append(" && !parts[").append(idx).append("].isBlank()) result.set").append(cap).append("(").append(parseFromString(jt, "parts[" + idx + "]")).append(");\n");
            idx++;
        }
        s.append("    return result;\n}\n");
        String fields = String.join(",", t.primaryKey().columns().stream().map(this::field).toList());
        String equality = t.primaryKey().columns().stream().map(name -> "Objects.equals(" + field(name) + ",value." + field(name) + ")").collect(java.util.stream.Collectors.joining("&&"));
        return s.append("@Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof ").append(n).append("Id value)) return false; return ").append(equality).append("; } @Override public int hashCode() { return Objects.hash(").append(fields).append("); } }\n").toString();
    }

    private String parseFromString(String jt, String expr) {
        return switch (jt) {
            case "java.util.UUID" -> "java.util.UUID.fromString(" + expr + ")";
            case "Integer" -> "Integer.valueOf(" + expr + ")";
            case "Long" -> "Long.valueOf(" + expr + ")";
            case "Boolean" -> "Boolean.valueOf(" + expr + ")";
            case "java.math.BigDecimal" -> "new java.math.BigDecimal(" + expr + ")";
            default -> expr;
        };
    }

    private void appendManyToMany(StringBuilder s, String table, SpringGenerationModel.ManyToMany relation) {
        boolean owner = relation.ownerTable().equals(table);
        if (owner) {
            s.append("@ManyToMany @JoinTable(name=\"").append(relation.joinTable().name()).append("\",").append(joinColumns("joinColumns", relation.ownerForeignKey())).append(',').append(joinColumns("inverseJoinColumns", relation.inverseForeignKey())).append(") private Set<").append(javaName(relation.inverseTable())).append("> ").append(relation.ownerField()).append("=new LinkedHashSet<>();\n");
            appendCollectionAccessors(s, javaName(relation.inverseTable()), relation.ownerField());
        }
        if (relation.inverseTable().equals(table)) {
            s.append("@ManyToMany(mappedBy=\"").append(relation.ownerField()).append("\") private Set<").append(javaName(relation.ownerTable())).append("> ").append(relation.inverseField()).append("=new LinkedHashSet<>();\n");
            appendCollectionAccessors(s, javaName(relation.ownerTable()), relation.inverseField());
        }
    }

    private String joinColumns(String attribute, ForeignKey foreignKey) {
        List<String> annotations = foreignKey.columns().stream().map(pair -> "@JoinColumn(name=\"" + pair.column() + "\",referencedColumnName=\"" + pair.referencedColumn() + "\")").toList();
        return attribute + "=" + (annotations.size() == 1 ? annotations.getFirst() : "{" + String.join(",", annotations) + "}");
    }

    private void appendCollectionAccessors(StringBuilder s, String target, String name) {
        String cap = cap(name);
        s.append("public Set<").append(target).append("> get").append(cap).append("() { return ").append(name).append("; } public void set").append(cap).append("(Set<").append(target).append("> ").append(name).append(") { this.").append(name).append('=').append(name).append("; }\n");
    }

    private String request(String b, String n, Table t) {
        return "package " + b + ".dto;\nimport jakarta.validation.constraints.*;\npublic record " + n + "Request(" + params(t, true) + ") {}\n";
    }

    private String response(String b, String n, Table t) {
        return "package " + b + ".dto;\npublic record " + n + "Response(" + params(t, false) + ") {}\n";
    }

    private String params(Table t, boolean v) {
        Set<String> pk = new LinkedHashSet<>(t.primaryKey().columns());
        boolean singlePk = pk.size() == 1;
        List<String> x = new ArrayList<>();
        for (Column c : t.columns()) {
            boolean requireNotNull = v && !c.nullable() && !(singlePk && pk.contains(c.name()));
            x.add((requireNotNull ? "@NotNull " : "") + javaType(c.sqlType()) + " " + field(c.name()));
        }
        return String.join(",", x);
    }

    private String repository(String b, String n, Table t) {
        return "package " + b + ".repository;\nimport " + b + ".entity.*;\nimport org.springframework.data.jpa.repository.JpaRepository;\npublic interface " + n + "Repository extends JpaRepository<" + n + "," + idType(t) + "> {}\n";
    }

    private String mapper(String b, String n, Table t) {
        Set<String> pk = new LinkedHashSet<>(t.primaryKey().columns());
        StringBuilder setCreate = new StringBuilder();
        StringBuilder setUpdate = new StringBuilder();
        StringBuilder args = new StringBuilder();
        for (Column c : t.columns()) {
            String f = field(c.name());
            String cap = cap(f);
            if (pk.contains(c.name()) && pk.size() == 1) {
                setCreate.append("if (r.").append(f).append("() != null) e.set").append(cap).append("(r.").append(f).append("());");
            } else {
                setCreate.append("e.set").append(cap).append("(r.").append(f).append("());");
            }
            if (!pk.contains(c.name())) {
                setUpdate.append("e.set").append(cap).append("(r.").append(f).append("());");
            }
            if (!args.isEmpty()) args.append(',');
            args.append("e.get").append(cap).append("()");
        }
        return "package " + b + ".mapper;\nimport " + b + ".entity." + n + ";\nimport " + b + ".dto.*;\nimport org.springframework.stereotype.Component;\n@Component public class " + n + "Mapper {\n    public " + n + " toEntity(" + n + "Request r) { var e = new " + n + "(); " + setCreate + " return e; }\n    public void update(" + n + " e, " + n + "Request r) { " + setUpdate + " }\n    public " + n + "Response response(" + n + " e) { return new " + n + "Response(" + args + "); }\n}\n";
    }

    private String service(String b, String n, Table t) {
        String idt = idType(t);
        StringBuilder autoId = new StringBuilder();
        if (!hasCompositeId(t)) {
            Column pkCol = id(t);
            String cap = cap(field(pkCol.name()));
            String jt = javaType(pkCol.sqlType());
            if ("java.util.UUID".equals(jt)) {
                autoId.append("if (e.get").append(cap).append("() == null) e.set").append(cap).append("(java.util.UUID.randomUUID());");
            } else if ("String".equals(jt)) {
                autoId.append("if (e.get").append(cap).append("() == null || e.get").append(cap).append("().isBlank()) e.set").append(cap).append("(java.util.UUID.randomUUID().toString());");
            } else if ("Integer".equals(jt)) {
                autoId.append("if (e.get").append(cap).append("() == null) e.set").append(cap).append("(repository.findAll().stream().map(").append(n).append("::get").append(cap).append(").filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1);");
            } else if ("Long".equals(jt)) {
                autoId.append("if (e.get").append(cap).append("() == null) e.set").append(cap).append("(repository.findAll().stream().map(").append(n).append("::get").append(cap).append(").filter(Objects::nonNull).max(Long::compareTo).orElse(0L) + 1L);");
            } else if ("java.math.BigDecimal".equals(jt)) {
                autoId.append("if (e.get").append(cap).append("() == null) e.set").append(cap).append("(java.math.BigDecimal.valueOf(repository.count() + 1));");
            }
        }
        return "package " + b + ".service;\nimport java.util.*;\nimport org.springframework.stereotype.Service;\nimport org.springframework.transaction.annotation.Transactional;\nimport " + b + ".entity.*;\nimport " + b + ".dto.*;\nimport " + b + ".repository.*;\nimport " + b + ".mapper.*;\nimport " + b + ".exception.*;\n@Service public class " + n + "Service {\n    private final " + n + "Repository repository;\n    private final " + n + "Mapper mapper;\n    public " + n + "Service(" + n + "Repository r, " + n + "Mapper m) { repository = r; mapper = m; }\n    @Transactional public " + n + "Response create(" + n + "Request r) { var e = mapper.toEntity(r); " + autoId + " return mapper.response(repository.save(e)); }\n    @Transactional(readOnly=true) public List<" + n + "Response> list() { return repository.findAll().stream().map(mapper::response).toList(); }\n    @Transactional(readOnly=true) public " + n + "Response get(" + idt + " id) { return mapper.response(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(\"" + n + " not found\"))); }\n    @Transactional public " + n + "Response update(" + idt + " id, " + n + "Request r) { var e = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(\"" + n + " not found\")); mapper.update(e, r); return mapper.response(repository.save(e)); }\n    @Transactional public void delete(" + idt + " id) { if (!repository.existsById(id)) throw new ResourceNotFoundException(\"" + n + " not found\"); repository.deleteById(id); }\n}\n";
    }

    private String controller(String b, String n, String r, Table t) {
        String idt = idType(t);
        return "package " + b + ".controller;\nimport java.util.*;\nimport jakarta.validation.Valid;\nimport org.springframework.http.*;\nimport org.springframework.web.bind.annotation.*;\nimport " + b + ".entity.*;\nimport " + b + ".dto.*;\nimport " + b + ".service.*;\n@RestController @RequestMapping(\"/api/" + r + "\") public class " + n + "Controller {\n    private final " + n + "Service service;\n    public " + n + "Controller(" + n + "Service s) { service = s; }\n    @PostMapping public ResponseEntity<" + n + "Response> create(@Valid @RequestBody " + n + "Request r) { return ResponseEntity.status(201).body(service.create(r)); }\n    @GetMapping public List<" + n + "Response> list() { return service.list(); }\n    @GetMapping(\"/{id}\") public " + n + "Response get(@PathVariable " + idt + " id) { return service.get(id); }\n    @PutMapping(\"/{id}\") public " + n + "Response update(@PathVariable " + idt + " id, @Valid @RequestBody " + n + "Request r) { return service.update(id, r); }\n    @DeleteMapping(\"/{id}\") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable " + idt + " id) { service.delete(id); }\n}\n";
    }

    private String schema(List<Table> ts) {
        StringBuilder s = new StringBuilder();
        for (Table t : ts) {
            s.append("CREATE TABLE ").append(t.name()).append(" (\n");
            List<String> x = new ArrayList<>();
            for (Column c : t.columns()) x.add("  " + c.name() + " " + c.sqlType() + (c.nullable() ? "" : " NOT NULL"));
            x.add("  CONSTRAINT " + t.primaryKey().name() + " PRIMARY KEY (" + String.join(",", t.primaryKey().columns()) + ")");
            for (Key u : t.uniqueConstraints()) x.add("  CONSTRAINT " + u.name() + " UNIQUE (" + String.join(",", u.columns()) + ")");
            for (Check c : t.checks()) x.add("  CONSTRAINT " + c.name() + " CHECK (" + c.expression() + ")");
            s.append(String.join(",\n", x)).append("\n);\n");
        }
        for (Table t : ts) {
            for (ForeignKey f : t.foreignKeys()) {
                s.append("ALTER TABLE ").append(t.name()).append(" ADD CONSTRAINT ").append(f.name()).append(" FOREIGN KEY (").append(pairs(f, true)).append(") REFERENCES ").append(f.referencedTable()).append(" (").append(pairs(f, false)).append(") ON DELETE ").append(f.onDelete()).append(";\n");
            }
        }
        return s.toString();
    }

    private String pairs(ForeignKey f, boolean a) {
        return String.join(",", f.columns().stream().map(p -> a ? p.column() : p.referencedColumn()).toList());
    }

    private String generatedAuthEntity(String b) {
        return "package " + b + ".auth;\nimport jakarta.persistence.*;\nimport java.time.Instant;\nimport java.util.UUID;\n@Entity @Table(name=\"app_users\") public class User {\n    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;\n    @Column(nullable=false,unique=true,length=254) private String email;\n    @Column(name=\"password_hash\",nullable=false,length=100) private String passwordHash;\n    @Column(name=\"display_name\",nullable=false,length=120) private String displayName;\n    @Column(name=\"created_at\",nullable=false) private Instant createdAt = Instant.now();\n    public UUID getId() { return id; }\n    public String getEmail() { return email; }\n    public void setEmail(String email) { this.email = email; }\n    public String getPasswordHash() { return passwordHash; }\n    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }\n    public String getDisplayName() { return displayName; }\n    public void setDisplayName(String displayName) { this.displayName = displayName; }\n    public Instant getCreatedAt() { return createdAt; }\n}\n";
    }

    private String generatedUserRepository(String b) {
        return "package " + b + ".auth;\nimport java.util.Optional;\nimport java.util.UUID;\nimport org.springframework.data.jpa.repository.JpaRepository;\npublic interface UserRepository extends JpaRepository<User,UUID> {\n    Optional<User> findByEmailIgnoreCase(String email);\n    boolean existsByEmailIgnoreCase(String email);\n}\n";
    }

    private String generatedRegisterRequest(String b) {
        return "package " + b + ".auth;\npublic record RegisterRequest(String email, String password, String displayName) {}\n";
    }

    private String generatedLoginRequest(String b) {
        return "package " + b + ".auth;\npublic record LoginRequest(String email, String password) {}\n";
    }

    private String generatedAuthResponse(String b) {
        return "package " + b + ".auth;\npublic record AuthResponse(String accessToken, String email, String displayName) {}\n";
    }

    private String generatedAuthService(String b) {
        return "package " + b + ".auth;\n" +
                "import java.nio.charset.StandardCharsets;\n" +
                "import java.util.Base64;\n" +
                "import java.util.UUID;\n" +
                "import org.springframework.boot.context.event.ApplicationReadyEvent;\n" +
                "import org.springframework.context.event.EventListener;\n" +
                "import org.springframework.http.HttpStatus;\n" +
                "import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;\n" +
                "import org.springframework.stereotype.Service;\n" +
                "import org.springframework.web.server.ResponseStatusException;\n" +
                "@Service public class AuthService {\n" +
                "    private final UserRepository users;\n" +
                "    private final BCryptPasswordEncoder encoder;\n" +
                "    public AuthService(UserRepository users, BCryptPasswordEncoder encoder) { this.users = users; this.encoder = encoder; }\n" +
                "    @EventListener(ApplicationReadyEvent.class)\n" +
                "    public void seedDemoUser() {\n" +
                "        try {\n" +
                "            if (!users.existsByEmailIgnoreCase(\"admin@demo.com\")) {\n" +
                "                User demo = new User();\n" +
                "                demo.setEmail(\"admin@demo.com\");\n" +
                "                demo.setPasswordHash(encoder.encode(\"admin1234\"));\n" +
                "                demo.setDisplayName(\"Administrador Demo\");\n" +
                "                users.save(demo);\n" +
                "            }\n" +
                "        } catch (Exception ignored) {}\n" +
                "    }\n" +
                "    public AuthResponse register(RegisterRequest request) {\n" +
                "        String email = request == null || request.email() == null ? \"\" : request.email().trim().toLowerCase();\n" +
                "        if (email.isBlank() || request.password() == null || request.password().length() < 6) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, \"Email válido y contraseña de al menos 6 caracteres requeridos\");\n" +
                "        if (users.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, \"El correo ya está registrado\");\n" +
                "        String name = request.displayName() == null || request.displayName().isBlank() ? email.split(\"@\")[0] : request.displayName().trim();\n" +
                "        User user = new User();\n" +
                "        user.setEmail(email);\n" +
                "        user.setPasswordHash(encoder.encode(request.password()));\n" +
                "        user.setDisplayName(name);\n" +
                "        users.save(user);\n" +
                "        return token(user);\n" +
                "    }\n" +
                "    public AuthResponse login(LoginRequest request) {\n" +
                "        String email = request == null || request.email() == null ? \"\" : request.email().trim().toLowerCase();\n" +
                "        User user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, \"Credenciales inválidas\"));\n" +
                "        if (request.password() == null || !encoder.matches(request.password(), user.getPasswordHash())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, \"Credenciales inválidas\");\n" +
                "        return token(user);\n" +
                "    }\n" +
                "    private AuthResponse token(User user) {\n" +
                "        String payload = user.getId() + \":\" + user.getEmail() + \":\" + UUID.randomUUID();\n" +
                "        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));\n" +
                "        return new AuthResponse(\"cg.\" + encoded, user.getEmail(), user.getDisplayName());\n" +
                "    }\n" +
                "}\n";
    }

    private String generatedAuthController(String b) {
        return "package " + b + ".auth;\nimport org.springframework.http.HttpStatus;\nimport org.springframework.web.bind.annotation.*;\n@RestController @RequestMapping(\"/api/auth\") public class AuthController {\n    private final AuthService service;\n    public AuthController(AuthService service) { this.service = service; }\n    @PostMapping(\"/register\") @ResponseStatus(HttpStatus.CREATED) public AuthResponse register(@RequestBody RegisterRequest request) { return service.register(request); }\n    @PostMapping(\"/login\") public AuthResponse login(@RequestBody LoginRequest request) { return service.login(request); }\n}\n";
    }

    private String generatedSecurityConfig(String b) {
        return "package " + b + ".auth;\n" +
                "import java.util.List;\n" +
                "import org.springframework.context.annotation.Bean;\n" +
                "import org.springframework.context.annotation.Configuration;\n" +
                "import org.springframework.security.config.Customizer;\n" +
                "import org.springframework.security.config.annotation.web.builders.HttpSecurity;\n" +
                "import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;\n" +
                "import org.springframework.security.web.SecurityFilterChain;\n" +
                "import org.springframework.web.cors.CorsConfiguration;\n" +
                "import org.springframework.web.cors.CorsConfigurationSource;\n" +
                "import org.springframework.web.cors.UrlBasedCorsConfigurationSource;\n" +
                "@Configuration public class SecurityConfig {\n" +
                "    @Bean BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }\n" +
                "    @Bean CorsConfigurationSource corsConfigurationSource() {\n" +
                "        CorsConfiguration config = new CorsConfiguration();\n" +
                "        config.setAllowedOriginPatterns(List.of(\"*\"));\n" +
                "        config.setAllowedMethods(List.of(\"GET\", \"POST\", \"PUT\", \"PATCH\", \"DELETE\", \"OPTIONS\"));\n" +
                "        config.setAllowedHeaders(List.of(\"*\"));\n" +
                "        config.setAllowCredentials(false);\n" +
                "        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();\n" +
                "        source.registerCorsConfiguration(\"/**\", config);\n" +
                "        return source;\n" +
                "    }\n" +
                "    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {\n" +
                "        http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll());\n" +
                "        return http.build();\n" +
                "    }\n" +
                "}\n";
    }

    private String generatedVoiceRequest(String b) {
        return "package " + b + ".ai;\npublic record VoiceCommandRequest(String transcript, String preferredTable) {}\n";
    }

    private String generatedVoiceResponse(String b) {
        return "package " + b + ".ai;\nimport java.util.Map;\npublic record VoiceCommandResponse(boolean executed, String action, String table, String entityLabel, Map<String, Object> extractedData, String message, String engine) {}\n";
    }

    private String generatedVoiceController(String b) {
        return "package " + b + ".ai;\n" +
                "import java.util.*;\n" +
                "import org.springframework.web.bind.annotation.*;\n" +
                "@RestController @RequestMapping(\"/api/ai\") public class VoiceAiController {\n" +
                "    private final VoiceAiService service;\n" +
                "    public VoiceAiController(VoiceAiService service) { this.service = service; }\n" +
                "    @GetMapping(\"/schema\") public List<Map<String, Object>> schema() { return service.schemaMetadata(); }\n" +
                "    @PostMapping(\"/voice-command\") public VoiceCommandResponse execute(@RequestBody VoiceCommandRequest request) { return service.processVoiceCommand(request); }\n" +
                "}\n";
    }

    private String generatedVoiceService(String b, List<Table> tables) {
        StringBuilder metaInit = new StringBuilder();
        for (Table t : tables) {
            String tName = t.name();
            String label = javaName(tName);
            String res = tName.replace('_', '-');
            metaInit.append("        tables.add(new TableMeta(\"").append(tName).append("\", \"").append(label).append("\", \"/api/").append(res).append("\", List.of(\n");
            List<String> cols = new ArrayList<>();
            Set<String> pks = new LinkedHashSet<>(t.primaryKey().columns());
            Map<String, String> fkMap = new HashMap<>();
            for (ForeignKey fk : t.foreignKeys()) {
                if (fk.columns().size() == 1) {
                    fkMap.put(fk.columns().getFirst().column(), fk.referencedTable());
                }
            }
            for (Column c : t.columns()) {
                String ref = fkMap.getOrDefault(c.name(), "");
                cols.add("            new ColMeta(\"" + c.name() + "\", \"" + field(c.name()) + "\", \"" + c.sqlType().toUpperCase(Locale.ROOT) + "\", " + c.nullable() + ", " + pks.contains(c.name()) + ", \"" + ref + "\")");
            }
            metaInit.append(String.join(",\n", cols)).append("\n        )));\n");
        }

        return "package " + b + ".ai;\n" +
                "import java.net.URI;\n" +
                "import java.net.http.*;\n" +
                "import java.sql.PreparedStatement;\n" +
                "import java.time.Duration;\n" +
                "import java.util.*;\n" +
                "import java.util.regex.*;\n" +
                "import org.springframework.http.HttpStatus;\n" +
                "import org.springframework.jdbc.core.JdbcTemplate;\n" +
                "import org.springframework.stereotype.Service;\n" +
                "import org.springframework.transaction.annotation.Transactional;\n" +
                "import org.springframework.web.server.ResponseStatusException;\n" +
                "import tools.jackson.databind.JsonNode;\n" +
                "import tools.jackson.databind.json.JsonMapper;\n\n" +
                "@Service public class VoiceAiService {\n" +
                "    public record ColMeta(String name, String fieldName, String sqlType, boolean nullable, boolean primaryKey, String referencedTable) {}\n" +
                "    public record TableMeta(String tableName, String entityLabel, String endpoint, List<ColMeta> columns) {}\n" +
                "    private final JdbcTemplate jdbc;\n" +
                "    private final List<TableMeta> tables = new ArrayList<>();\n" +
                "    private final JsonMapper json = JsonMapper.builder().build();\n\n" +
                "    public VoiceAiService(JdbcTemplate jdbc) {\n" +
                "        this.jdbc = jdbc;\n" +
                metaInit +
                "    }\n\n" +
                "    public List<Map<String, Object>> schemaMetadata() {\n" +
                "        List<Map<String, Object>> result = new ArrayList<>();\n" +
                "        for (TableMeta t : tables) {\n" +
                "            Map<String, Object> item = new LinkedHashMap<>();\n" +
                "            item.put(\"tableName\", t.tableName());\n" +
                "            item.put(\"entityLabel\", t.entityLabel());\n" +
                "            item.put(\"endpoint\", t.endpoint());\n" +
                "            List<String> editable = t.columns().stream().filter(c -> !c.primaryKey() || t.columns().size() == 1).map(ColMeta::fieldName).toList();\n" +
                "            String sampleFields = t.columns().stream().filter(c -> !c.primaryKey()).limit(3).map(c -> c.fieldName() + \" \" + sampleSpokenValue(c)).reduce((a, b2) -> a + \", \" + b2).orElse(\"id 1\");\n" +
                "            item.put(\"voiceExample\", \"Crear en \" + t.entityLabel() + \": \" + sampleFields);\n" +
                "            item.put(\"columns\", t.columns());\n" +
                "            result.add(item);\n" +
                "        }\n" +
                "        return result;\n" +
                "    }\n\n" +
                "    @Transactional\n" +
                "    public VoiceCommandResponse processVoiceCommand(VoiceCommandRequest request) {\n" +
                "        if (request == null || request.transcript() == null || request.transcript().isBlank()) {\n" +
                "            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, \"El comando de voz está vacío\");\n" +
                "        }\n" +
                "        if (tables.isEmpty()) {\n" +
                "            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, \"No hay tablas disponibles en el modelo\");\n" +
                "        }\n" +
                "        String raw = request.transcript().trim();\n" +
                "        String engine = \"Motor Semántico Local CASEGen\";\n" +
                "        TableMeta target = resolveTable(raw, request.preferredTable());\n" +
                "        Map<String, Object> extracted = new LinkedHashMap<>();\n" +
                "        Map<String, Object> ollamaData = tryOllamaExtraction(raw, target);\n" +
                "        if (ollamaData != null && !ollamaData.isEmpty()) {\n" +
                "            extracted.putAll(ollamaData);\n" +
                "            engine = \"Ollama LLM Local + Validador CASEGen\";\n" +
                "        } else {\n" +
                "            extracted.putAll(extractFieldsHeuristically(raw, target));\n" +
                "        }\n" +
                "        Map<String, Object> rowToInsert = prepareDatabaseRow(target, extracted, raw);\n" +
                "        insertIntoDatabase(target, rowToInsert);\n" +
                "        String summary = rowToInsert.entrySet().stream().map(e -> e.getKey() + \"=\" + e.getValue()).reduce((a, b2) -> a + \", \" + b2).orElse(\"\");\n" +
                "        String msg = \"Registro insertado por voz en \" + target.entityLabel() + \" (\" + summary + \")\";\n" +
                "        return new VoiceCommandResponse(true, \"INSERT\", target.tableName(), target.entityLabel(), rowToInsert, msg, engine);\n" +
                "    }\n\n" +
                "    private TableMeta resolveTable(String transcript, String preferred) {\n" +
                "        if (preferred != null && !preferred.isBlank()) {\n" +
                "            for (TableMeta t : tables) {\n" +
                "                if (t.tableName().equalsIgnoreCase(preferred.trim()) || t.entityLabel().equalsIgnoreCase(preferred.trim())) return t;\n" +
                "            }\n" +
                "        }\n" +
                "        String norm = normalize(transcript);\n" +
                "        for (TableMeta t : tables) {\n" +
                "            String tn = normalize(t.tableName().replace('_', ' '));\n" +
                "            String el = normalize(t.entityLabel());\n" +
                "            if (norm.contains(tn) || norm.contains(el)) return t;\n" +
                "            for (String alias : spanishAliases(t.tableName())) {\n" +
                "                if (norm.contains(alias)) return t;\n" +
                "            }\n" +
                "        }\n" +
                "        return tables.getFirst();\n" +
                "    }\n\n" +
                "    private List<String> spanishAliases(String tableName) {\n" +
                "        String low = tableName.toLowerCase(Locale.ROOT);\n" +
                "        if (low.contains(\"customer\") || low.contains(\"client\")) return List.of(\"cliente\", \"clientes\", \"customer\");\n" +
                "        if (low.contains(\"product\") || low.contains(\"item\")) return List.of(\"producto\", \"productos\", \"articulo\", \"item\");\n" +
                "        if (low.contains(\"sale\") || low.contains(\"order\") || low.contains(\"invoice\")) return List.of(\"venta\", \"ventas\", \"pedido\", \"orden\", \"factura\");\n" +
                "        if (low.contains(\"category\")) return List.of(\"categoria\", \"categorias\");\n" +
                "        if (low.contains(\"user\") || low.contains(\"employee\") || low.contains(\"person\")) return List.of(\"usuario\", \"empleado\", \"persona\");\n" +
                "        return List.of(low);\n" +
                "    }\n\n" +
                "    private Map<String, Object> tryOllamaExtraction(String transcript, TableMeta target) {\n" +
                "        try {\n" +
                "            String ollamaUrl = System.getenv().getOrDefault(\"OLLAMA_URL\", \"http://localhost:11434/api/generate\");\n" +
                "            StringBuilder cols = new StringBuilder();\n" +
                "            for (ColMeta c : target.columns()) {\n" +
                "                if (!c.primaryKey()) cols.append(c.name()).append(\" (\").append(c.sqlType()).append(\"), \");\n" +
                "            }\n" +
                "            String prompt = \"Extrae los valores para insertar en la tabla \" + target.tableName() + \" con columnas: \" + cols + \". Texto de voz: \\\"\" + transcript.replace(\"\\\"\", \"'\") + \"\\\". Responde SOLO un objeto JSON plano con las claves de columna.\";\n" +
                "            String body = json.writeValueAsString(Map.of(\"model\", System.getenv().getOrDefault(\"OLLAMA_MODEL\", \"llama3.2\"), \"prompt\", prompt, \"stream\", false, \"format\", \"json\"));\n" +
                "            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(700)).build();\n" +
                "            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(ollamaUrl)).timeout(Duration.ofMillis(2500)).header(\"Content-Type\", \"application/json\").POST(HttpRequest.BodyPublishers.ofString(body)).build();\n" +
                "            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());\n" +
                "            if (res.statusCode() == 200) {\n" +
                "                JsonNode root = json.readTree(res.body());\n" +
                "                JsonNode parsed = json.readTree(root.path(\"response\").asText(\"\\u007B\\u007D\"));\n" +
                "                Map<String, Object> out = new LinkedHashMap<>();\n" +
                "                for (ColMeta c : target.columns()) {\n" +
                "                    if (parsed.hasNonNull(c.name())) out.put(c.name(), parsed.get(c.name()).asText());\n" +
                "                    else if (parsed.hasNonNull(c.fieldName())) out.put(c.name(), parsed.get(c.fieldName()).asText());\n" +
                "                }\n" +
                "                return out;\n" +
                "            }\n" +
                "        } catch (Exception ignored) {}\n" +
                "        return null;\n" +
                "    }\n\n" +
                "    private Map<String, Object> extractFieldsHeuristically(String transcript, TableMeta target) {\n" +
                "        Map<String, Object> out = new LinkedHashMap<>();\n" +
                "        String cleaned = transcript.replaceAll(\"(?i)^(crear|crea|insertar|inserta|agregar|agrega|registrar|registra|nuevo|nueva)\\\\s+(un|una|el|la|en|registro\\\\s+en)?\\\\s*\", \"\").trim();\n" +
                "        List<ColMeta> nonPk = target.columns().stream().filter(c -> !c.primaryKey()).toList();\n" +
                "        for (ColMeta c : nonPk) {\n" +
                "            List<String> keys = columnKeywords(c);\n" +
                "            for (String key : keys) {\n" +
                "                Pattern p = Pattern.compile(\"(?i)\\\\b\" + Pattern.quote(key) + \"\\\\b\\\\s*(?:es|igual\\\\s+a|de|:|=)?\\\\s*([^,;.]+?)(?=\\\\s+(?:y\\\\s+|con\\\\s+)?(?:nombre|name|correo|email|precio|price|total|monto|cantidad|stock|activo|active|estado|status|descripcion|description|telefono|phone|fecha|date|codigo|code|[a-z_]+\\\\s*[:=])|$)\");\n" +
                "                Matcher m = p.matcher(cleaned);\n" +
                "                if (m.find()) {\n" +
                "                    String candidate = m.group(1).trim().replaceAll(\"(?i)\\\\s+(y|con)$\", \"\").trim();\n" +
                "                    if (!candidate.isBlank()) {\n" +
                "                        out.put(c.name(), candidate);\n" +
                "                        break;\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        if (out.isEmpty() && !nonPk.isEmpty()) {\n" +
                "            ColMeta firstText = nonPk.stream().filter(c -> c.sqlType().contains(\"CHAR\") || c.sqlType().contains(\"TEXT\")).findFirst().orElse(nonPk.getFirst());\n" +
                "            String fallback = cleaned.replaceFirst(\"(?i)^\" + Pattern.quote(target.tableName().replace('_', ' ')) + \"\\\\s*:?\\\\s*\", \"\").replaceFirst(\"(?i)^\" + Pattern.quote(target.entityLabel()) + \"\\\\s*:?\\\\s*\", \"\").trim();\n" +
                "            for (String alias : spanishAliases(target.tableName())) {\n" +
                "                fallback = fallback.replaceFirst(\"(?i)^\" + Pattern.quote(alias) + \"\\\\s*:?\\\\s*\", \"\").trim();\n" +
                "            }\n" +
                "            if (!fallback.isBlank()) out.put(firstText.name(), fallback);\n" +
                "        }\n" +
                "        return out;\n" +
                "    }\n\n" +
                "    private List<String> columnKeywords(ColMeta c) {\n" +
                "        Set<String> keys = new LinkedHashSet<>();\n" +
                "        keys.add(c.name().replace('_', ' '));\n" +
                "        keys.add(c.name());\n" +
                "        keys.add(c.fieldName());\n" +
                "        String low = c.name().toLowerCase(Locale.ROOT);\n" +
                "        if (low.contains(\"name\") || low.contains(\"nombre\")) { keys.add(\"nombre\"); keys.add(\"llamado\"); keys.add(\"llamada\"); keys.add(\"name\"); }\n" +
                "        if (low.contains(\"email\") || low.contains(\"correo\") || low.contains(\"mail\")) { keys.add(\"correo\"); keys.add(\"email\"); keys.add(\"mail\"); }\n" +
                "        if (low.contains(\"price\") || low.contains(\"precio\") || low.contains(\"amount\") || low.contains(\"total\")) { keys.add(\"precio\"); keys.add(\"monto\"); keys.add(\"total\"); keys.add(\"valor\"); }\n" +
                "        if (low.contains(\"active\") || low.contains(\"activo\") || low.contains(\"enabled\")) { keys.add(\"activo\"); keys.add(\"estado\"); keys.add(\"active\"); }\n" +
                "        if (low.contains(\"phone\") || low.contains(\"telefono\") || low.contains(\"celular\")) { keys.add(\"telefono\"); keys.add(\"celular\"); keys.add(\"phone\"); }\n" +
                "        if (low.contains(\"description\") || low.contains(\"descripcion\") || low.contains(\"detalle\")) { keys.add(\"descripcion\"); keys.add(\"detalle\"); }\n" +
                "        if (low.contains(\"stock\") || low.contains(\"quantity\") || low.contains(\"cantidad\")) { keys.add(\"cantidad\"); keys.add(\"stock\"); }\n" +
                "        return new ArrayList<>(keys);\n" +
                "    }\n\n" +
                "    private Map<String, Object> prepareDatabaseRow(TableMeta target, Map<String, Object> extracted, String rawTranscript) {\n" +
                "        Map<String, Object> row = new LinkedHashMap<>();\n" +
                "        for (ColMeta c : target.columns()) {\n" +
                "            Object rawVal = extracted.get(c.name());\n" +
                "            if (c.primaryKey()) {\n" +
                "                if (rawVal != null && !String.valueOf(rawVal).isBlank()) {\n" +
                "                    row.put(c.name(), coerceValue(c, String.valueOf(rawVal), rawTranscript));\n" +
                "                } else if (!c.referencedTable().isBlank()) {\n" +
                "                    row.put(c.name(), resolveForeignKeyValue(c));\n" +
                "                } else {\n" +
                "                    row.put(c.name(), generatePrimaryKeyValue(target.tableName(), c));\n" +
                "                }\n" +
                "                continue;\n" +
                "            }\n" +
                "            if (rawVal != null && !String.valueOf(rawVal).isBlank()) {\n" +
                "                row.put(c.name(), coerceValue(c, String.valueOf(rawVal), rawTranscript));\n" +
                "            } else if (!c.referencedTable().isBlank()) {\n" +
                "                Object fkVal = resolveForeignKeyValue(c);\n" +
                "                if (fkVal != null || !c.nullable()) row.put(c.name(), fkVal);\n" +
                "            } else if (!c.nullable()) {\n" +
                "                row.put(c.name(), defaultColumnValue(c, rawTranscript));\n" +
                "            }\n" +
                "        }\n" +
                "        return row;\n" +
                "    }\n\n" +
                "    private Object generatePrimaryKeyValue(String tableName, ColMeta c) {\n" +
                "        String t = c.sqlType();\n" +
                "        if (t.equals(\"UUID\")) return UUID.randomUUID();\n" +
                "        if (t.equals(\"INTEGER\")) {\n" +
                "            Integer max = jdbc.queryForObject(\"SELECT COALESCE(MAX(\" + c.name() + \"), 0) + 1 FROM \" + tableName, Integer.class);\n" +
                "            return max == null ? 1 : max;\n" +
                "        }\n" +
                "        if (t.equals(\"BIGINT\")) {\n" +
                "            Long max = jdbc.queryForObject(\"SELECT COALESCE(MAX(\" + c.name() + \"), 0) + 1 FROM \" + tableName, Long.class);\n" +
                "            return max == null ? 1L : max;\n" +
                "        }\n" +
                "        if (t.startsWith(\"NUMERIC\")) return java.math.BigDecimal.ONE;\n" +
                "        return UUID.randomUUID().toString();\n" +
                "    }\n\n" +
                "    private Object resolveForeignKeyValue(ColMeta c) {\n" +
                "        try {\n" +
                "            TableMeta refTable = tables.stream().filter(t -> t.tableName().equalsIgnoreCase(c.referencedTable())).findFirst().orElse(null);\n" +
                "            String pkCol = refTable != null && !refTable.columns().isEmpty() ? refTable.columns().stream().filter(ColMeta::primaryKey).map(ColMeta::name).findFirst().orElse(\"id\") : \"id\";\n" +
                "            List<Object> existing = jdbc.query(\"SELECT \" + pkCol + \" FROM \" + c.referencedTable() + \" LIMIT 1\", (rs, i) -> rs.getObject(1));\n" +
                "            if (!existing.isEmpty()) return existing.getFirst();\n" +
                "            if (refTable != null) {\n" +
                "                Map<String, Object> parentRow = prepareDatabaseRow(refTable, Map.of(), \"Auto-creado por Agente de Voz\");\n" +
                "                insertIntoDatabase(refTable, parentRow);\n" +
                "                return parentRow.get(pkCol);\n" +
                "            }\n" +
                "        } catch (Exception ignored) {}\n" +
                "        return c.sqlType().equals(\"UUID\") ? UUID.randomUUID() : null;\n" +
                "    }\n\n" +
                "    private Object coerceValue(ColMeta c, String raw, String fullTranscript) {\n" +
                "        String t = c.sqlType();\n" +
                "        String clean = raw.trim();\n" +
                "        try {\n" +
                "            if (t.equals(\"UUID\")) return UUID.fromString(clean);\n" +
                "        } catch (Exception e) {\n" +
                "            if (!c.referencedTable().isBlank()) return resolveForeignKeyValue(c);\n" +
                "            return UUID.randomUUID();\n" +
                "        }\n" +
                "        if (t.equals(\"BOOLEAN\")) {\n" +
                "            String low = normalize(clean);\n" +
                "            return low.contains(\"true\") || low.contains(\"verdadero\") || low.equals(\"si\") || low.contains(\"activo\") || low.equals(\"1\");\n" +
                "        }\n" +
                "        if (t.equals(\"INTEGER\")) return parseSpokenNumber(clean).intValue();\n" +
                "        if (t.equals(\"BIGINT\")) return parseSpokenNumber(clean).longValue();\n" +
                "        if (t.startsWith(\"NUMERIC\")) return parseSpokenNumber(clean);\n" +
                "        if (c.name().toLowerCase(Locale.ROOT).contains(\"email\") || c.name().toLowerCase(Locale.ROOT).contains(\"correo\")) {\n" +
                "            return clean.toLowerCase(Locale.ROOT).replace(\" arroba \", \"@\").replace(\" punto \", \".\").replaceAll(\"\\\\s+\", \"\");\n" +
                "        }\n" +
                "        return clean;\n" +
                "    }\n\n" +
                "    private Object defaultColumnValue(ColMeta c, String transcript) {\n" +
                "        String t = c.sqlType();\n" +
                "        if (t.equals(\"BOOLEAN\")) return !normalize(transcript).contains(\"inactivo\") && !normalize(transcript).contains(\"falso\");\n" +
                "        if (t.equals(\"INTEGER\")) return 1;\n" +
                "        if (t.equals(\"BIGINT\")) return 1L;\n" +
                "        if (t.startsWith(\"NUMERIC\")) return java.math.BigDecimal.valueOf(10.0);\n" +
                "        if (t.equals(\"UUID\")) return UUID.randomUUID();\n" +
                "        if (c.name().toLowerCase(Locale.ROOT).contains(\"email\") || c.name().toLowerCase(Locale.ROOT).contains(\"correo\")) return \"voz.\" + (System.currentTimeMillis() % 10000) + \"@demo.com\";\n" +
                "        return \"Registro por voz\";\n" +
                "    }\n\n" +
                "    private java.math.BigDecimal parseSpokenNumber(String text) {\n" +
                "        String norm = normalize(text).replace(\",\", \".\");\n" +
                "        Matcher m = Pattern.compile(\"-?\\\\d+(?:\\\\.\\\\d+)?\").matcher(norm);\n" +
                "        if (m.find()) return new java.math.BigDecimal(m.group());\n" +
                "        Map<String, Integer> words = Map.of(\"cero\", 0, \"uno\", 1, \"una\", 1, \"dos\", 2, \"tres\", 3, \"cuatro\", 4, \"cinco\", 5, \"seis\", 6, \"siete\", 7, \"ocho\", 8);\n" +
                "        for (var entry : words.entrySet()) {\n" +
                "            if (norm.contains(entry.getKey())) return java.math.BigDecimal.valueOf(entry.getValue());\n" +
                "        }\n" +
                "        if (norm.contains(\"diez\")) return java.math.BigDecimal.TEN;\n" +
                "        if (norm.contains(\"cien\")) return java.math.BigDecimal.valueOf(100);\n" +
                "        return java.math.BigDecimal.ONE;\n" +
                "    }\n\n" +
                "    private void insertIntoDatabase(TableMeta target, Map<String, Object> row) {\n" +
                "        List<String> cols = new ArrayList<>(row.keySet());\n" +
                "        String placeholders = String.join(\",\", Collections.nCopies(cols.size(), \"?\"));\n" +
                "        String sql = \"INSERT INTO \" + target.tableName() + \" (\" + String.join(\",\", cols) + \") VALUES (\" + placeholders + \")\";\n" +
                "        jdbc.update(con -> {\n" +
                "            PreparedStatement ps = con.prepareStatement(sql);\n" +
                "            for (int i = 0; i < cols.size(); i++) {\n" +
                "                ps.setObject(i + 1, row.get(cols.get(i)));\n" +
                "            }\n" +
                "            return ps;\n" +
                "        });\n" +
                "    }\n\n" +
                "    private String sampleSpokenValue(ColMeta c) {\n" +
                "        String low = c.name().toLowerCase(Locale.ROOT);\n" +
                "        if (low.contains(\"email\") || low.contains(\"correo\")) return \"ana@demo.com\";\n" +
                "        if (low.contains(\"name\") || low.contains(\"nombre\")) return \"Ana Gomez\";\n" +
                "        if (c.sqlType().equals(\"BOOLEAN\")) return \"verdadero\";\n" +
                "        if (c.sqlType().equals(\"INTEGER\") || c.sqlType().equals(\"BIGINT\") || c.sqlType().startsWith(\"NUMERIC\")) return \"25\";\n" +
                "        return \"valor demo\";\n" +
                "    }\n\n" +
                "    private static String normalize(String s) {\n" +
                "        if (s == null) return \"\";\n" +
                "        return java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll(\"\\\\p\\u007BInCombiningDiacriticalMarks\\u007D+\", \"\");\n" +
                "    }\n" +
                "}\n";
    }

    private String authSchema() {
        return "CREATE TABLE app_users (\n  id UUID PRIMARY KEY,\n  email VARCHAR(254) NOT NULL UNIQUE,\n  password_hash VARCHAR(100) NOT NULL,\n  display_name VARCHAR(120) NOT NULL,\n  created_at TIMESTAMPTZ NOT NULL\n);\n\n";
    }

    private String pom(JsonNode p) {
        return ("<?xml version=\"1.0\"?><project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>%s</version><relativePath/></parent><groupId>%s</groupId><artifactId>%s</artifactId><version>0.0.1-SNAPSHOT</version><properties><java.version>%d</java.version><testcontainers.version>2.0.3</testcontainers.version></properties><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency><dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>3.1.1</version></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency><dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-postgresql</artifactId><version>$" + "{testcontainers.version}</version><scope>test</scope></dependency></dependencies><build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build></project>\n")
                .formatted(p.path("springBootVersion").asText(), p.path("groupId").asText(), p.path("artifactId").asText(), p.path("javaVersion").asInt());
    }

    private String handler(String b) {
        return "package " + b + ".exception;\n" +
                "import java.util.Map;\n" +
                "import org.springframework.dao.DataIntegrityViolationException;\n" +
                "import org.springframework.http.*;\n" +
                "import org.springframework.web.bind.MethodArgumentNotValidException;\n" +
                "import org.springframework.web.bind.annotation.*;\n" +
                "import org.springframework.web.server.ResponseStatusException;\n" +
                "@RestControllerAdvice public class GlobalExceptionHandler {\n" +
                "    @ExceptionHandler(ResourceNotFoundException.class)\n" +
                "    ResponseEntity<Map<String, String>> missing(ResourceNotFoundException e) {\n" +
                "        return ResponseEntity.status(404).body(Map.of(\"code\", \"NOT_FOUND\", \"message\", e.getMessage()));\n" +
                "    }\n" +
                "    @ExceptionHandler(ResponseStatusException.class)\n" +
                "    ResponseEntity<Map<String, String>> status(ResponseStatusException e) {\n" +
                "        return ResponseEntity.status(e.getStatusCode()).body(Map.of(\"code\", e.getStatusCode().toString(), \"message\", e.getReason() == null ? \"Error en la solicitud\" : e.getReason()));\n" +
                "    }\n" +
                "    @ExceptionHandler(MethodArgumentNotValidException.class)\n" +
                "    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {\n" +
                "        String detail = e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + \": \" + f.getDefaultMessage()).findFirst().orElse(\"Datos de entrada inválidos\");\n" +
                "        return ResponseEntity.status(400).body(Map.of(\"code\", \"VALIDATION_ERROR\", \"message\", detail));\n" +
                "    }\n" +
                "    @ExceptionHandler(DataIntegrityViolationException.class)\n" +
                "    ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {\n" +
                "        return ResponseEntity.status(409).body(Map.of(\"code\", \"DATA_INTEGRITY_ERROR\", \"message\", \"Conflicto de integridad: verifique claves foráneas o valores únicos\"));\n" +
                "    }\n" +
                "    @ExceptionHandler(Exception.class)\n" +
                "    ResponseEntity<Map<String, String>> unexpected(Exception e) {\n" +
                "        return ResponseEntity.status(500).body(Map.of(\"code\", \"INTERNAL_ERROR\", \"message\", e.getMessage() == null ? \"Unexpected error\" : e.getMessage()));\n" +
                "    }\n" +
                "}\n";
    }

    private String applicationYaml() {
        return "server:\n  address: 0.0.0.0\n  port: $" + "{SERVER_PORT:8080}\nspring:\n  datasource:\n    url: $" + "{SPRING_DATASOURCE_URL}\n    username: $" + "{SPRING_DATASOURCE_USERNAME}\n    password: $" + "{SPRING_DATASOURCE_PASSWORD}\n  jpa:\n    hibernate:\n      ddl-auto: validate\n  flyway:\n    enabled: true\nspringdoc:\n  swagger-ui:\n    path: /swagger-ui.html\n";
    }

    private String devEnvironment(String database, String username, String password) {
        return "DB_NAME=" + database + "\nDB_USERNAME=" + username + "\nDB_PASSWORD=" + password + "\nDB_HOST=localhost\nDB_PORT=5432\nAPP_PORT=8080\n";
    }

    private String exampleEnvironment(String database, String username) {
        return "# Isolated settings for this generated backend\nDB_NAME=" + database + "\nDB_USERNAME=" + username + "\nDB_PASSWORD=change_this_generated_dev_password\n# Used by Docker Compose and local run scripts\nDB_HOST=localhost\nDB_PORT=5432\nAPP_PORT=8080\n";
    }

    private String dockerfile(int javaVersion) {
        return "FROM eclipse-temurin:" + javaVersion + "-jdk-alpine AS build\nWORKDIR /workspace\nCOPY . .\nRUN chmod +x mvnw && ./mvnw -q -DskipTests package\n\nFROM eclipse-temurin:" + javaVersion + "-jre-alpine\nWORKDIR /app\nCOPY --from=build /workspace/target/*.jar app.jar\nEXPOSE 8080\nENTRYPOINT [\"java\",\"-jar\",\"/app/app.jar\"]\n";
    }

    private String compose() {
        return "services:\n" +
                "  postgres:\n" +
                "    image: postgres:17-alpine\n" +
                "    environment:\n" +
                "      POSTGRES_DB: $" + "{DB_NAME}\n" +
                "      POSTGRES_USER: $" + "{DB_USERNAME}\n" +
                "      POSTGRES_PASSWORD: $" + "{DB_PASSWORD}\n" +
                "    ports:\n" +
                "      - \"$" + "{DB_PORT:-5432}:5432\"\n" +
                "    healthcheck:\n" +
                "      test: [\"CMD-SHELL\", \"pg_isready -U $" + "{DB_USERNAME} -d $" + "{DB_NAME}\"]\n" +
                "      interval: 5s\n" +
                "      timeout: 5s\n" +
                "      retries: 20\n" +
                "    volumes:\n" +
                "      - database_data:/var/lib/postgresql/data\n" +
                "    networks:\n" +
                "      - backend_network\n" +
                "  app:\n" +
                "    build:\n" +
                "      context: .\n" +
                "      dockerfile: Dockerfile\n" +
                "    depends_on:\n" +
                "      postgres:\n" +
                "        condition: service_healthy\n" +
                "    environment:\n" +
                "      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/$" + "{DB_NAME}\n" +
                "      SPRING_DATASOURCE_USERNAME: $" + "{DB_USERNAME}\n" +
                "      SPRING_DATASOURCE_PASSWORD: $" + "{DB_PASSWORD}\n" +
                "      SERVER_PORT: 8080\n" +
                "    ports:\n" +
                "      - \"$" + "{APP_PORT:-8080}:8080\"\n" +
                "    networks:\n" +
                "      - backend_network\n" +
                "volumes:\n" +
                "  database_data:\n" +
                "networks:\n" +
                "  backend_network:\n" +
                "    driver: bridge\n";
    }

    private String runPowerShell() {
        return "$ErrorActionPreference = 'Stop'\n$envFile = Join-Path $PSScriptRoot '.env'\nif (-not (Test-Path $envFile)) { throw 'Missing .env file.' }\nGet-Content $envFile | ForEach-Object {\n    if ($_ -match '^[A-Za-z_][A-Za-z0-9_]*=') {\n        $name, $value = $_.Split('=', 2)\n        Set-Item -Path \"Env:$name\" -Value $value\n    }\n}\n# Always overwrite datasource variables inherited from Windows/CASEGen.\n$env:SPRING_DATASOURCE_URL = \"jdbc:postgresql://$($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)\"\n$env:SPRING_DATASOURCE_USERNAME = $env:DB_USERNAME\n$env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD\n$env:SERVER_PORT = $env:APP_PORT\n& (Join-Path $PSScriptRoot 'mvnw.cmd') spring-boot:run\nexit $LASTEXITCODE\n";
    }

    private String runShell() {
        return "#!/usr/bin/env sh\nset -eu\nSCRIPT_DIR=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\nset -a\n. \"$SCRIPT_DIR/.env\"\nset +a\n# Always overwrite datasource variables inherited from the host.\nSPRING_DATASOURCE_URL=\"jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}\"\nSPRING_DATASOURCE_USERNAME=\"${DB_USERNAME}\"\nSPRING_DATASOURCE_PASSWORD=\"${DB_PASSWORD}\"\nSERVER_PORT=\"${APP_PORT}\"\nexport SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD SERVER_PORT\nexec \"$SCRIPT_DIR/mvnw\" spring-boot:run\n";
    }

    private String startAllPowerShell() {
        return "$ErrorActionPreference = 'Stop'\n" +
                "Write-Host '=== 1. Iniciando PostgreSQL en Docker ===' -ForegroundColor Cyan\n" +
                "docker compose up -d postgres\n" +
                "$lanIp = (\n" +
                "    Get-NetIPAddress -AddressFamily IPv4 |\n" +
                "    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.PrefixOrigin -ne 'WellKnown' } |\n" +
                "    Select-Object -First 1 -ExpandProperty IPAddress\n" +
                ")\n" +
                "if (-not $lanIp) { $lanIp = 'localhost' }\n" +
                "Write-Host \"=== 2. Configurando IP LAN ($lanIp) para Expo Go ===\" -ForegroundColor Cyan\n" +
                "$mobileEnv = Join-Path $PSScriptRoot 'mobile\\.env'\n" +
                "if (Test-Path (Join-Path $PSScriptRoot 'mobile')) {\n" +
                "    Set-Content -Path $mobileEnv -Value \"EXPO_PUBLIC_API_BASE_URL=http://${lanIp}:8080\"\n" +
                "}\n" +
                "Write-Host '=== 3. Iniciando Backend Spring Boot en segundo plano ===' -ForegroundColor Cyan\n" +
                "Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'run.ps1')\n" +
                "if (Test-Path (Join-Path $PSScriptRoot 'mobile')) {\n" +
                "    Write-Host '=== 4. Iniciando App Movil (Expo Go) ===' -ForegroundColor Cyan\n" +
                "    Set-Location (Join-Path $PSScriptRoot 'mobile')\n" +
                "    if (-not (Test-Path 'node_modules')) { npm install }\n" +
                "    npx expo start\n" +
                "}\n";
    }

    private String startAllShell() {
        return "#!/usr/bin/env sh\n" +
                "set -eu\n" +
                "SCRIPT_DIR=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\n" +
                "echo '=== 1. Iniciando PostgreSQL en Docker ==='\n" +
                "docker compose -f \"$SCRIPT_DIR/docker-compose.yml\" up -d postgres\n" +
                "echo '=== 2. Iniciando Backend Spring Boot ==='\n" +
                "\"$SCRIPT_DIR/run.sh\" &\n" +
                "if [ -d \"$SCRIPT_DIR/mobile\" ]; then\n" +
                "  cd \"$SCRIPT_DIR/mobile\"\n" +
                "  [ -d node_modules ] || npm install\n" +
                "  npx expo start\n" +
                "fi\n";
    }

    private String readme(String artifact, String database, Traceability trace) {
        return ("""
                # %s — Aplicación Full-Stack Generada (Backend + Móvil Expo Go + IA por Voz)

                Proyecto Full-Stack generado determinísticamente por CASEGen.
                Incluye en una sola carpeta:
                - **Raíz (`./`)**: Backend Spring Boot + PostgreSQL en Docker + Agente de IA Local por Voz.
                - **Carpeta `./mobile`**: Aplicación móvil React Native lista para **Expo Go** y Web con diseño formal, Login y CRUD completo.

                ## Inicio Rápido Todo-en-Uno (Windows / Linux / macOS)

                ```powershell
                # En Windows (levanta PostgreSQL en Docker, detecta tu IP Wi-Fi, inicia Backend y abre Expo Go):
                powershell -ExecutionPolicy Bypass -File .\\start-all.ps1
                ```

                ## Opción A: Base de Datos y Backend con Docker

                El archivo `.env` contiene credenciales DEV aisladas y generadas exclusivamente para este artifact:

                ```bash
                # Levantar todo (PostgreSQL + Backend) en contenedores:
                docker compose up --build -d

                # O levantar únicamente la base de datos PostgreSQL en Docker (puerto 5432 expuesto) y correr el backend local:
                docker compose up -d postgres
                .\\run.ps1
                ```

                La base `%s`, su usuario, volumen y red pertenecen al proyecto Compose de este artifact.

                ## Opción B: App Móvil con Expo Go (`./mobile`)

                ```bash
                cd mobile
                npm install
                npx expo start
                ```

                - **Credenciales Demo precargadas**: `admin@demo.com` / `admin1234` (o usa el botón **Acceso Rápido Demo** en la pantalla de Login).
                - **Conexión automática en Expo Go**: La app detecta automáticamente la IP LAN de tu computadora desde Metro Bundler y también permite editar la URL del servidor directamente desde la pantalla de Login.
                - **Agente de IA Local por Voz**: Pulsa el botón **Asistente de Voz IA** en la app móvil para dictar comandos en lenguaje natural (ej. *"Crear cliente con nombre Ana Gomez, correo ana@demo.com"*) e insertarlos directamente en PostgreSQL (compatible con **Ollama local** en `localhost:11434` y con el **Motor Semántico Local integrado** sin dependencias externas).

                ## Configuración

                La aplicación consume `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
                `SPRING_DATASOURCE_PASSWORD` y `SERVER_PORT`. Docker Compose los entrega explícitamente desde
                los valores `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` y `APP_PORT` de `.env`.
                `.env` está excluido por `.gitignore`; `.env.example` documenta la plantilla.

                ## OpenAPI y Swagger UI

                Swagger UI: http://localhost:8080/swagger-ui.html

                OpenAPI JSON: http://localhost:8080/v3/api-docs

                ## Postman

                Importa `postman/%s.postman_collection.json`. La variable `baseUrl` apunta a `http://localhost:8080`.

                ## Trazabilidad CASEGen

                - Project: `%s`
                - UmlModel: %s (`%s`)
                - ModelVersion: v%s (`%s`)
                - LogicalModel: `%s`
                - GenerationRun: ejecución registrada en el historial de CASEGen; allí se conserva su UUID operativo.
                - artifactId: `%s`

                Generated from ModelVersion v%s.
                """).formatted(artifact, database, artifact, trace.projectId(), markdown(trace.modelName()), trace.modelId(),
                trace.modelVersionNumber(), trace.modelVersionId(), trace.logicalModelId(), artifact, trace.modelVersionNumber());
    }

    private String postman(String artifact, List<Table> tables) {
        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("info", Map.of("_postman_id", UUID.nameUUIDFromBytes((artifact + ":postman").getBytes(StandardCharsets.UTF_8)).toString(), "name", artifact + " API", "schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"));
        collection.put("variable", List.of(Map.of("key", "baseUrl", "value", "http://localhost:8080")));
        List<Object> folders = new ArrayList<>();
        for (Table table : tables) {
            String resource = table.name().replace('_', '-'), idExample = String.valueOf(example(id(table)));
            String body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(exampleBody(table));
            List<Object> requests = new ArrayList<>();
            requests.add(request("POST " + resource, "POST", "{{baseUrl}}/api/" + resource, body));
            requests.add(request("GET " + resource, "GET", "{{baseUrl}}/api/" + resource, null));
            requests.add(request("GET " + resource + " by id", "GET", "{{baseUrl}}/api/" + resource + "/" + idExample, null));
            requests.add(request("PUT " + resource, "PUT", "{{baseUrl}}/api/" + resource + "/" + idExample, body));
            requests.add(request("DELETE " + resource, "DELETE", "{{baseUrl}}/api/" + resource + "/" + idExample, null));
            folders.add(Map.of("name", javaName(table.name()), "item", requests));
        }
        collection.put("item", folders);
        return tools.jackson.databind.json.JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(collection) + "\n";
    }

    private Map<String, Object> request(String name, String method, String url, String body) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", method);
        request.put("header", body == null ? List.of() : List.of(Map.of("key", "Content-Type", "value", "application/json")));
        request.put("url", Map.of("raw", url, "host", List.of("{{baseUrl}}"), "path", Arrays.stream(url.substring("{{baseUrl}}/".length()).split("/")).toList()));
        if (body != null) request.put("body", Map.of("mode", "raw", "raw", body, "options", Map.of("raw", Map.of("language", "json"))));
        return Map.of("name", name, "request", request, "response", List.of());
    }

    private Map<String, Object> exampleBody(Table table) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (Column column : table.columns()) body.put(field(column.name()), example(column));
        return body;
    }

    private String databaseName(String artifact) {
        String value = artifact.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (value.isBlank()) value = "generated_backend";
        return value.substring(0, Math.min(value.length(), 63));
    }

    private String developmentPassword(String artifact) {
        String value = UUID.nameUUIDFromBytes(("casegen-generated-dev:" + artifact).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return "dev_" + value.substring(0, 20);
    }

    private String markdown(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('`', '\'');
    }

    private Object example(Column column) {
        String type = column.sqlType().toUpperCase(Locale.ROOT);
        if (type.startsWith("VARCHAR") || type.equals("TEXT")) return "string";
        if (type.equals("INTEGER") || type.equals("BIGINT")) return 1;
        if (type.equals("BOOLEAN")) return true;
        if (type.startsWith("NUMERIC")) return 1.0;
        if (type.equals("UUID")) return "00000000-0000-0000-0000-000000000001";
        return "value";
    }

    private String generateSeedDataSql(List<Table> tables) {
        List<Table> sorted = topologicalSort(tables);
        StringBuilder sql = new StringBuilder();
        sql.append("-- ==========================================================\n");
        sql.append("-- V2__seed_test_data.sql: 10 registros de prueba por entidad\n");
        sql.append("-- Generado por CASEGen para pruebas y demostracion inmediata\n");
        sql.append("-- ==========================================================\n\n");

        Map<String, Table> tableMap = new LinkedHashMap<>();
        for (Table t : tables) tableMap.put(t.name(), t);

        for (Table t : sorted) {
            sql.append("-- Entidad: ").append(javaName(t.name())).append(" (").append(t.name()).append(")\n");
            List<Column> cols = t.columns();
            String colList = cols.stream().map(Column::name).reduce((a, b) -> a + ", " + b).orElse("");

            for (int i = 1; i <= 10; i++) {
                StringBuilder valList = new StringBuilder();
                for (int cIdx = 0; cIdx < cols.size(); cIdx++) {
                    if (cIdx > 0) valList.append(", ");
                    Column col = cols.get(cIdx);
                    valList.append(generateMockSqlValue(t, col, i, tableMap));
                }
                sql.append("INSERT INTO ").append(t.name())
                   .append(" (").append(colList).append(") VALUES (")
                   .append(valList).append(");\n");
            }
            sql.append("\n");
        }
        return sql.toString();
    }

    private List<Table> topologicalSort(List<Table> tables) {
        Map<String, Table> map = new LinkedHashMap<>();
        for (Table t : tables) map.put(t.name(), t);
        List<Table> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (Table t : tables) {
            topoDfs(t, map, visited, visiting, result);
        }
        return result;
    }

    private void topoDfs(Table t, Map<String, Table> map, Set<String> visited, Set<String> visiting, List<Table> result) {
        if (visited.contains(t.name())) return;
        if (visiting.contains(t.name())) return;
        visiting.add(t.name());
        for (ForeignKey fk : t.foreignKeys()) {
            Table parent = map.get(fk.referencedTable());
            if (parent != null && !parent.name().equals(t.name())) {
                topoDfs(parent, map, visited, visiting, result);
            }
        }
        visiting.remove(t.name());
        visited.add(t.name());
        result.add(t);
    }

    private String generateMockSqlValue(Table t, Column col, int row, Map<String, Table> tableMap) {
        for (ForeignKey fk : t.foreignKeys()) {
            for (ColumnPair cp : fk.columns()) {
                if (cp.column().equalsIgnoreCase(col.name())) {
                    Table refTable = tableMap.get(fk.referencedTable());
                    if (refTable != null) {
                        Column refPk = id(refTable);
                        return generateMockIdValue(refPk.sqlType(), row);
                    }
                    return String.valueOf(row);
                }
            }
        }

        if (t.primaryKey() != null && t.primaryKey().columns().contains(col.name())) {
            return generateMockIdValue(col.sqlType(), row);
        }

        String colNameLow = col.name().toLowerCase(Locale.ROOT);
        String sqlType = col.sqlType().toUpperCase(Locale.ROOT);
        String label = javaName(t.name());

        if (sqlType.equals("BOOLEAN")) {
            return (row % 2 == 1) ? "true" : "false";
        }
        if (sqlType.equals("UUID")) {
            return String.format(Locale.ROOT, "'a%07d-0000-0000-0000-%012d'", row, row);
        }
        if (colNameLow.contains("name") || colNameLow.contains("nombre")) {
            String[] samples = {"Alfa", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliet"};
            return "'" + label + " " + samples[(row - 1) % samples.length] + "'";
        }
        if (colNameLow.contains("email") || colNameLow.contains("correo") || colNameLow.contains("mail")) {
            return "'" + t.name().toLowerCase(Locale.ROOT) + row + "@example.com'";
        }
        if (colNameLow.contains("phone") || colNameLow.contains("telefono") || colNameLow.contains("celular")) {
            return "'7" + String.format(Locale.ROOT, "%07d", 100000 + row) + "'";
        }
        if (colNameLow.contains("address") || colNameLow.contains("direccion")) {
            return "'Av. Principal #" + (row * 100) + "'";
        }
        if (colNameLow.contains("price") || colNameLow.contains("precio") || colNameLow.contains("cost") || colNameLow.contains("monto") || colNameLow.contains("total")) {
            return String.format(Locale.ROOT, "%.2f", 10.0 + (row * 15.5));
        }
        if (colNameLow.contains("stock") || colNameLow.contains("cantidad") || colNameLow.contains("quantity")) {
            return String.valueOf(row * 10);
        }
        if (colNameLow.contains("date") || colNameLow.contains("fecha")) {
            return "'2026-03-" + String.format(Locale.ROOT, "%02d", Math.min(row, 28)) + "'";
        }
        if (colNameLow.contains("desc") || colNameLow.contains("detalle") || colNameLow.contains("obs")) {
            return "'Registro " + label + " numero " + row + " para pruebas'";
        }
        if (colNameLow.contains("status") || colNameLow.contains("estado")) {
            return (row % 2 == 1) ? "'ACTIVO'" : "'PENDIENTE'";
        }
        if (colNameLow.contains("code") || colNameLow.contains("codigo")) {
            return "'COD-" + String.format(Locale.ROOT, "%04d", row * 10) + "'";
        }
        if (sqlType.equals("INTEGER") || sqlType.equals("BIGINT")) {
            return String.valueOf(row * 5);
        }
        if (sqlType.startsWith("NUMERIC") || sqlType.contains("DECIMAL") || sqlType.contains("FLOAT") || sqlType.contains("DOUBLE")) {
            return String.format(Locale.ROOT, "%.2f", row * 20.0);
        }

        String res = "'" + label + " " + col.name() + " " + row + "'";
        if (sqlType.startsWith("VARCHAR(")) {
            try {
                int maxLen = Integer.parseInt(sqlType.substring(8, sqlType.length() - 1));
                if (res.length() > maxLen + 2) {
                    String trimmed = res.substring(1, res.length() - 1);
                    if (trimmed.length() > maxLen) trimmed = trimmed.substring(0, maxLen);
                    return "'" + trimmed + "'";
                }
            } catch (Exception ignored) {}
        }
        return res;
    }

    private String generateMockIdValue(String sqlType, int row) {
        String t = sqlType.toUpperCase(Locale.ROOT);
        if (t.equals("UUID")) {
            return String.format(Locale.ROOT, "'00000000-0000-0000-0000-%012d'", row);
        }
        if (t.equals("INTEGER") || t.equals("BIGINT")) {
            return String.valueOf(row);
        }
        return "'ID-" + row + "'";
    }

    private String generatedSyncMutationItem(String b) {
        return "package " + b + ".sync;\n" +
                "import java.util.Map;\n" +
                "public record SyncMutationItem(String clientId, String entityName, String operation, String targetId, Map<String, Object> payload) {}\n";
    }

    private String generatedSyncMutationResult(String b) {
        return "package " + b + ".sync;\n" +
                "public record SyncMutationResult(String clientId, boolean success, String message, Object data) {}\n";
    }

    private String generatedSyncBatchRequest(String b) {
        return "package " + b + ".sync;\n" +
                "import java.util.List;\n" +
                "public record SyncBatchRequest(List<SyncMutationItem> mutations) {}\n";
    }

    private String generatedSyncBatchResponse(String b) {
        return "package " + b + ".sync;\n" +
                "import java.util.List;\n" +
                "public record SyncBatchResponse(List<SyncMutationResult> results, int processedCount, int successCount) {}\n";
    }

    private String generatedSyncController(String b) {
        return "package " + b + ".sync;\n" +
                "import io.swagger.v3.oas.annotations.Operation;\n" +
                "import io.swagger.v3.oas.annotations.tags.Tag;\n" +
                "import org.springframework.web.bind.annotation.*;\n" +
                "@RestController @RequestMapping(\"/api/sync\") @Tag(name = \"Sync\", description = \"Sincronización Offline-First\")\n" +
                "public class SyncController {\n" +
                "    private final SyncService service;\n" +
                "    public SyncController(SyncService service) { this.service = service; }\n" +
                "    @PostMapping(\"/batch\") @Operation(summary = \"Sincronizar lote de mutaciones offline\")\n" +
                "    public SyncBatchResponse batch(@RequestBody SyncBatchRequest request) { return service.processBatch(request); }\n" +
                "}\n";
    }

    private String generatedSyncService(String b, List<Table> tables) {
        StringBuilder tableMetas = new StringBuilder();
        for (Table t : tables) {
            String pkCol = id(t).name();
            String pkType = id(t).sqlType();
            List<String> colInits = new ArrayList<>();
            for (Column c : t.columns()) {
                colInits.add("            new SyncCol(\"" + c.name() + "\", \"" + field(c.name()) + "\", \"" + c.sqlType() + "\", " + c.nullable() + ")");
            }
            tableMetas.append("        tables.add(new SyncTable(\"").append(t.name()).append("\", \"").append(javaName(t.name())).append("\", \"").append(pkCol).append("\", \"").append(pkType).append("\", List.of(\n")
                    .append(String.join(",\n", colInits)).append("\n        )));\n");
        }

        return "package " + b + ".sync;\n" +
                "import org.springframework.jdbc.core.JdbcTemplate;\n" +
                "import org.springframework.stereotype.Service;\n" +
                "import org.springframework.transaction.annotation.Transactional;\n" +
                "import java.util.*;\n\n" +
                "@Service public class SyncService {\n" +
                "    public record SyncCol(String name, String fieldName, String sqlType, boolean nullable) {}\n" +
                "    public record SyncTable(String tableName, String entityLabel, String pkCol, String pkType, List<SyncCol> cols) {}\n" +
                "    private final JdbcTemplate jdbc;\n" +
                "    private final List<SyncTable> tables = new ArrayList<>();\n\n" +
                "    public SyncService(JdbcTemplate jdbc) {\n" +
                "        this.jdbc = jdbc;\n" +
                tableMetas +
                "    }\n\n" +
                "    @Transactional\n" +
                "    public SyncBatchResponse processBatch(SyncBatchRequest request) {\n" +
                "        if (request == null || request.mutations() == null || request.mutations().isEmpty()) {\n" +
                "            return new SyncBatchResponse(List.of(), 0, 0);\n" +
                "        }\n" +
                "        List<SyncMutationResult> results = new ArrayList<>();\n" +
                "        int successCount = 0;\n" +
                "        for (SyncMutationItem item : request.mutations()) {\n" +
                "            try {\n" +
                "                SyncMutationResult r = processSingle(item);\n" +
                "                results.add(r);\n" +
                "                if (r.success()) successCount++;\n" +
                "            } catch (Exception e) {\n" +
                "                results.add(new SyncMutationResult(item.clientId(), false, e.getMessage(), null));\n" +
                "            }\n" +
                "        }\n" +
                "        return new SyncBatchResponse(results, request.mutations().size(), successCount);\n" +
                "    }\n\n" +
                "    private SyncMutationResult processSingle(SyncMutationItem item) {\n" +
                "        SyncTable t = resolve(item.entityName());\n" +
                "        if (t == null) return new SyncMutationResult(item.clientId(), false, \"Entidad no encontrada: \" + item.entityName(), null);\n" +
                "        String op = item.operation() == null ? \"CREATE\" : item.operation().toUpperCase(Locale.ROOT);\n" +
                "        Map<String, Object> payload = item.payload() == null ? Map.of() : item.payload();\n" +
                "        if (\"CREATE\".equals(op)) {\n" +
                "            return doCreate(item.clientId(), t, payload);\n" +
                "        } else if (\"UPDATE\".equals(op)) {\n" +
                "            return doUpdate(item.clientId(), t, item.targetId(), payload);\n" +
                "        } else if (\"DELETE\".equals(op)) {\n" +
                "            return doDelete(item.clientId(), t, item.targetId(), payload);\n" +
                "        }\n" +
                "        return new SyncMutationResult(item.clientId(), false, \"Operacion no soportada: \" + op, null);\n" +
                "    }\n\n" +
                "    private SyncTable resolve(String name) {\n" +
                "        if (name == null) return null;\n" +
                "        String n = name.trim().replace(\"-\", \"_\");\n" +
                "        for (SyncTable t : tables) {\n" +
                "            if (t.tableName().equalsIgnoreCase(n) || t.entityLabel().equalsIgnoreCase(n)) return t;\n" +
                "        }\n" +
                "        return null;\n" +
                "    }\n\n" +
                "    private SyncMutationResult doCreate(String cid, SyncTable t, Map<String, Object> payload) {\n" +
                "        List<String> colNames = new ArrayList<>();\n" +
                "        List<String> placeholders = new ArrayList<>();\n" +
                "        List<Object> values = new ArrayList<>();\n" +
                "        for (SyncCol col : t.cols()) {\n" +
                "            Object val = payload.get(col.name());\n" +
                "            if (val == null) val = payload.get(col.fieldName());\n" +
                "            if (col.name().equalsIgnoreCase(t.pkCol())) {\n" +
                "                if (val != null && !String.valueOf(val).isBlank() && !String.valueOf(val).startsWith(\"local_\")) {\n" +
                "                    colNames.add(col.name()); placeholders.add(\"?\"); values.add(coerce(col, val));\n" +
                "                } else if (col.sqlType().equalsIgnoreCase(\"UUID\")) {\n" +
                "                    colNames.add(col.name()); placeholders.add(\"?\"); values.add(UUID.randomUUID());\n" +
                "                } else if (col.sqlType().equalsIgnoreCase(\"BIGINT\") || col.sqlType().equalsIgnoreCase(\"INTEGER\")) {\n" +
                "                    Long max = jdbc.queryForObject(\"SELECT COALESCE(MAX(\" + col.name() + \"), 0) + 1 FROM \" + t.tableName(), Long.class);\n" +
                "                    colNames.add(col.name()); placeholders.add(\"?\"); values.add(max == null ? 1L : max);\n" +
                "                }\n" +
                "            } else if (val != null) {\n" +
                "                colNames.add(col.name()); placeholders.add(\"?\"); values.add(coerce(col, val));\n" +
                "            }\n" +
                "        }\n" +
                "        String sql = \"INSERT INTO \" + t.tableName() + \" (\" + String.join(\", \", colNames) + \") VALUES (\" + String.join(\", \", placeholders) + \")\";\n" +
                "        jdbc.update(sql, values.toArray());\n" +
                "        return new SyncMutationResult(cid, true, \"Registro creado via sincronizacion\", payload);\n" +
                "    }\n\n" +
                "    private SyncMutationResult doUpdate(String cid, SyncTable t, String targetId, Map<String, Object> payload) {\n" +
                "        String idVal = targetId;\n" +
                "        if (idVal == null) {\n" +
                "            Object raw = payload.get(t.pkCol());\n" +
                "            if (raw == null) raw = payload.get(\"id\");\n" +
                "            if (raw != null) idVal = String.valueOf(raw);\n" +
                "        }\n" +
                "        if (idVal == null || idVal.isBlank()) return new SyncMutationResult(cid, false, \"ID objetivo ausente para UPDATE\", null);\n" +
                "        List<String> setClauses = new ArrayList<>();\n" +
                "        List<Object> values = new ArrayList<>();\n" +
                "        for (SyncCol col : t.cols()) {\n" +
                "            if (col.name().equalsIgnoreCase(t.pkCol())) continue;\n" +
                "            Object val = payload.get(col.name());\n" +
                "            if (val == null) val = payload.get(col.fieldName());\n" +
                "            if (val != null) {\n" +
                "                setClauses.add(col.name() + \" = ?\");\n" +
                "                values.add(coerce(col, val));\n" +
                "            }\n" +
                "        }\n" +
                "        if (setClauses.isEmpty()) return new SyncMutationResult(cid, true, \"Sin campos a actualizar\", null);\n" +
                "        values.add(coercePk(t, idVal));\n" +
                "        String sql = \"UPDATE \" + t.tableName() + \" SET \" + String.join(\", \", setClauses) + \" WHERE \" + t.pkCol() + \" = ?\";\n" +
                "        int count = jdbc.update(sql, values.toArray());\n" +
                "        return new SyncMutationResult(cid, count > 0, count > 0 ? \"Registro actualizado\" : \"Registro no encontrado con id \" + idVal, payload);\n" +
                "    }\n\n" +
                "    private SyncMutationResult doDelete(String cid, SyncTable t, String targetId, Map<String, Object> payload) {\n" +
                "        String idVal = targetId;\n" +
                "        if (idVal == null && payload != null) {\n" +
                "            Object raw = payload.get(t.pkCol());\n" +
                "            if (raw == null) raw = payload.get(\"id\");\n" +
                "            if (raw != null) idVal = String.valueOf(raw);\n" +
                "        }\n" +
                "        if (idVal == null || idVal.isBlank()) return new SyncMutationResult(cid, false, \"ID objetivo ausente para DELETE\", null);\n" +
                "        String sql = \"DELETE FROM \" + t.tableName() + \" WHERE \" + t.pkCol() + \" = ?\";\n" +
                "        int count = jdbc.update(sql, coercePk(t, idVal));\n" +
                "        return new SyncMutationResult(cid, count > 0, count > 0 ? \"Registro eliminado\" : \"Registro no encontrado\", null);\n" +
                "    }\n\n" +
                "    private Object coerce(SyncCol col, Object val) {\n" +
                "        if (val == null) return null;\n" +
                "        String s = String.valueOf(val).trim();\n" +
                "        String t = col.sqlType().toUpperCase(Locale.ROOT);\n" +
                "        try {\n" +
                "            if (t.equals(\"UUID\")) return UUID.fromString(s);\n" +
                "            if (t.equals(\"INTEGER\")) return Integer.valueOf(s);\n" +
                "            if (t.equals(\"BIGINT\")) return Long.valueOf(s);\n" +
                "            if (t.equals(\"BOOLEAN\")) return Boolean.valueOf(s);\n" +
                "            if (t.startsWith(\"NUMERIC\")) return new java.math.BigDecimal(s);\n" +
                "        } catch (Exception ignored) {}\n" +
                "        return s;\n" +
                "    }\n\n" +
                "    private Object coercePk(SyncTable t, String s) {\n" +
                "        if (t.pkType().equalsIgnoreCase(\"UUID\")) {\n" +
                "            try { return UUID.fromString(s.trim()); } catch (Exception ignored) {}\n" +
                "        }\n" +
                "        if (t.pkType().equalsIgnoreCase(\"BIGINT\")) {\n" +
                "            try { return Long.valueOf(s.trim()); } catch (Exception ignored) {}\n" +
                "        }\n" +
                "        if (t.pkType().equalsIgnoreCase(\"INTEGER\")) {\n" +
                "            try { return Integer.valueOf(s.trim()); } catch (Exception ignored) {}\n" +
                "        }\n" +
                "        return s;\n" +
                "    }\n" +
                "}\n";
    }

    private String fastStartPowerShell() {
        return "$ErrorActionPreference = 'Stop'\n" +
                "Write-Host '==================================================' -ForegroundColor Cyan\n" +
                "Write-Host '   CASEGen Fast-Start: Backend + DB + Expo Go    ' -ForegroundColor Cyan\n" +
                "Write-Host '==================================================' -ForegroundColor Cyan\n\n" +
                "# 1. Verificar puerto 5432 / 5433\n" +
                "$port = 5432\n" +
                "$tcp = New-Object System.Net.Sockets.TcpClient\n" +
                "try {\n" +
                "    $tcp.Connect('127.0.0.1', 5432)\n" +
                "    $tcp.Close()\n" +
                "    Write-Host '[!] Puerto 5432 ocupado. Asignando DB_PORT=5433 para Docker.' -ForegroundColor Yellow\n" +
                "    $port = 5433\n" +
                "} catch {\n" +
                "    Write-Host '[+] Puerto 5432 libre para PostgreSQL.' -ForegroundColor Green\n" +
                "}\n\n" +
                "# 2. Cargar .env\n" +
                "if (Test-Path '.env') {\n" +
                "    Get-Content '.env' | ForEach-Object {\n" +
                "        if ($_ -match '^[A-Za-z_][A-Za-z0-9_]*=') {\n" +
                "            $k, $v = $_.Split('=', 2)\n" +
                "            if ($k -eq 'DB_PORT' -and $port -eq 5433) { $v = '5433' }\n" +
                "            Set-Item -Path \"Env:$k\" -Value $v\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "$env:DB_PORT = \"$port\"\n" +
                "$env:SPRING_DATASOURCE_URL = \"jdbc:postgresql://localhost:$port/$($env:DB_NAME)\"\n" +
                "$env:SPRING_DATASOURCE_USERNAME = $env:DB_USERNAME\n" +
                "$env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD\n\n" +
                "# 3. Iniciar BD Docker\n" +
                "Write-Host '[*] Levantando base de datos PostgreSQL con Docker...' -ForegroundColor Cyan\n" +
                "docker compose up -d postgres\n" +
                "$retries = 20\n" +
                "while ($retries -gt 0) {\n" +
                "    try {\n" +
                "        $t = New-Object System.Net.Sockets.TcpClient\n" +
                "        $t.Connect('127.0.0.1', $port)\n" +
                "        $t.Close()\n" +
                "        Write-Host \"[+] Base de datos conectada en puerto $port.\" -ForegroundColor Green\n" +
                "        break\n" +
                "    } catch {\n" +
                "        Start-Sleep -Seconds 1\n" +
                "        $retries--\n" +
                "    }\n" +
                "}\n\n" +
                "# 4. Auto-detectar IP LAN para Expo Go\n" +
                "$lanIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notmatch '^(127\\.|169\\.254\\.)' -and $_.InterfaceAlias -notmatch 'Loopback|vEthernet|Virtual' } | Select-Object -First 1).IPAddress\n" +
                "if (-not $lanIp) { $lanIp = 'localhost' }\n" +
                "Write-Host \"[+] IP LAN detectada para conexion movil: $lanIp\" -ForegroundColor Green\n\n" +
                "# 5. Inyectar IP en mobile/.env\n" +
                "if (Test-Path 'mobile') {\n" +
                "    Set-Content 'mobile/.env' \"EXPO_PUBLIC_API_BASE_URL=http://$($lanIp):8080`n\"\n" +
                "    Write-Host \"[+] mobile/.env configurado con EXPO_PUBLIC_API_BASE_URL=http://$($lanIp):8080\" -ForegroundColor Green\n" +
                "}\n\n" +
                "# 6. Iniciar Spring Boot Backend con optimizaciones de arranque veloz\n" +
                "Write-Host '[*] Levantando Spring Boot Backend...' -ForegroundColor Cyan\n" +
                "$backendCmd = \"Set-Location '$PSScriptRoot'; .\\mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments='-XX:TieredStopAtLevel=1 -Dspring.main.lazy-initialization=true'\"\n" +
                "Start-Process powershell -ArgumentList '-NoExit', '-Command', $backendCmd\n\n" +
                "# 7. Iniciar Expo Mobile\n" +
                "if (Test-Path 'mobile') {\n" +
                "    Write-Host '[*] Levantando React Native Expo Go...' -ForegroundColor Cyan\n" +
                "    $mobileCmd = \"Set-Location '$PSScriptRoot\\mobile'; if (-not (Test-Path 'node_modules')) { npm install }; npx expo start\"\n" +
                "    Start-Process powershell -ArgumentList '-NoExit', '-Command', $mobileCmd\n" +
                "}\n\n" +
                "Write-Host '==================================================' -ForegroundColor Green\n" +
                "Write-Host ' Sistema iniciado exitosamente!' -ForegroundColor Green\n" +
                "Write-Host ' - Swagger UI: http://localhost:8080/swagger-ui.html' -ForegroundColor Green\n" +
                "Write-Host \" - API LAN:    http://$($lanIp):8080/api\" -ForegroundColor Green\n" +
                "Write-Host ' - Expo QR:    Disponible en la ventana de terminal movil' -ForegroundColor Green\n" +
                "Write-Host '==================================================' -ForegroundColor Green\n";
    }

    private String fastStartShell() {
        return "#!/usr/bin/env bash\n" +
                "set -e\n" +
                "echo \"==================================================\"\n" +
                "echo \"   CASEGen Fast-Start: Backend + DB + Expo Go    \"\n" +
                "echo \"==================================================\"\n" +
                "PORT=5432\n" +
                "if nc -z 127.0.0.1 5432 2>/dev/null; then\n" +
                "  echo \"[!] Puerto 5432 ocupado. Usando 5433.\"\n" +
                "  PORT=5433\n" +
                "fi\n" +
                "export DB_PORT=$PORT\n" +
                "if [ -f .env ]; then\n" +
                "  export $(grep -v '^#' .env | xargs)\n" +
                "fi\n" +
                "export DB_PORT=$PORT\n" +
                "export SPRING_DATASOURCE_URL=\"jdbc:postgresql://localhost:${PORT}/${DB_NAME}\"\n" +
                "export SPRING_DATASOURCE_USERNAME=\"${DB_USERNAME}\"\n" +
                "export SPRING_DATASOURCE_PASSWORD=\"${DB_PASSWORD}\"\n" +
                "docker compose up -d postgres\n" +
                "LAN_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo \"localhost\")\n" +
                "if [ -z \"$LAN_IP\" ]; then LAN_IP=\"localhost\"; fi\n" +
                "echo \"[+] IP LAN detectada: $LAN_IP\"\n" +
                "if [ -d mobile ]; then\n" +
                "  echo \"EXPO_PUBLIC_API_BASE_URL=http://${LAN_IP}:8080\" > mobile/.env\n" +
                "fi\n" +
                "echo \"[*] Iniciando Spring Boot Backend con optimizaciones...\"\n" +
                "./mvnw spring-boot:run -Dspring-boot.run.jvmArguments=\"-XX:TieredStopAtLevel=1 -Dspring.main.lazy-initialization=true\" &\n" +
                "if [ -d mobile ]; then\n" +
                "  cd mobile\n" +
                "  [ ! -d node_modules ] && npm install\n" +
                "  npx expo start\n" +
                "fi\n";
    }

    private String contextTest(String b) {
        return "package " + b + ";\nimport org.junit.jupiter.api.Test;\nimport org.springframework.boot.test.context.SpringBootTest;\n@SpringBootTest class ApplicationContextTest {\n    @Test void contextLoads() {}\n}\n";
    }

    private void copyWrapper(Path root) throws IOException {
        Path src = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(src.resolve("mvnw"))) src = src.resolve("backend");
        for (String f : List.of("mvnw", "mvnw.cmd", ".mvn/wrapper/maven-wrapper.properties")) {
            Path from = src.resolve(f);
            if (!Files.exists(from)) throw new IOException("Maven Wrapper source missing: " + from);
            Path to = root.resolve(f);
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void put(Path root, String rel, String value) throws IOException {
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root)) throw new IOException("Unsafe generated path");
        Files.createDirectories(p.getParent());
        Files.writeString(p, value, StandardCharsets.UTF_8);
    }

    private boolean hasCompositeId(Table t) {
        return t.primaryKey() != null && t.primaryKey().columns().size() > 1;
    }

    private String idType(Table t) {
        return hasCompositeId(t) ? javaName(t.name()) + "Id" : javaType(id(t).sqlType());
    }

    private Column column(Table t, String name) {
        return t.columns().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private Column id(Table t) {
        return t.columns().stream().filter(c -> t.primaryKey().columns().contains(c.name())).findFirst().orElseThrow();
    }

    private String javaType(String sql) {
        String s = sql.toUpperCase(Locale.ROOT);
        if (s.startsWith("VARCHAR") || s.equals("TEXT")) return "String";
        if (s.equals("INTEGER")) return "Integer";
        if (s.equals("BIGINT")) return "Long";
        if (s.equals("BOOLEAN")) return "Boolean";
        if (s.startsWith("NUMERIC")) return "java.math.BigDecimal";
        if (s.equals("UUID")) return "java.util.UUID";
        return "String";
    }

    private String javaName(String s) {
        StringBuilder r = new StringBuilder();
        for (String p : s.split("[-_]")) r.append(cap(p));
        return r.toString();
    }

    private String field(String s) {
        String n = javaName(s);
        return Character.toLowerCase(n.charAt(0)) + n.substring(1);
    }

    private String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String columnOptions(String sql) {
        String s = sql.toUpperCase(Locale.ROOT);
        if (s.startsWith("VARCHAR(")) return ",length=" + s.substring(8, s.length() - 1);
        if (s.startsWith("NUMERIC(19,2)")) return ",precision=19,scale=2";
        return "";
    }
}
