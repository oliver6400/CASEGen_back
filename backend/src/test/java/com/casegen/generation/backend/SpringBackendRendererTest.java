package com.casegen.generation.backend;

import static com.casegen.generation.logical.dto.LogicalModelDtos.*;
import static org.junit.jupiter.api.Assertions.*;

import com.casegen.generation.backend.service.SpringBackendRenderer;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class SpringBackendRendererTest {
    private final JsonMapper json=JsonMapper.builder().build();

    @Test
    void rendersExecutableDocumentationOpenApiAndPostmanFromLogicalTypes(@TempDir Path workspace)throws Exception {
        var profile=json.valueToTree(Map.of("profile",Map.of("basePackage","com.example.sales","artifactId","ventas-api","groupId","com.example","javaVersion",21,"springBootVersion","4.1.1")));
        var table=new Table("customer",UUID.randomUUID().toString(),null,List.of(
                new Column("id",UUID.randomUUID().toString(),"UUID",false,1),
                new Column("name",UUID.randomUUID().toString(),"VARCHAR(255)",false,2),
                new Column("active",UUID.randomUUID().toString(),"BOOLEAN",true,3)),
                new Key("pk_customer",List.of("id"),null),List.of(),List.of(),List.of(),List.of());
        var input=new Response(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),profile,"VALID","checksum",UUID.randomUUID(),Instant.now(),List.of(table),List.of());

        var trace=new SpringBackendRenderer.Traceability(UUID.randomUUID(),UUID.randomUUID(),"Ventas",
                input.modelVersionId(),3,input.id());
        new SpringBackendRenderer().render(workspace,input,trace);

        assertTrue(Files.exists(workspace.resolve("pom.xml")));assertTrue(Files.exists(workspace.resolve("mvnw")));assertTrue(Files.exists(workspace.resolve("mvnw.cmd")));
        assertTrue(Files.readString(workspace.resolve("pom.xml")).contains("springdoc-openapi-starter-webmvc-ui"));
        assertTrue(Files.readString(workspace.resolve("README.md")).contains("swagger-ui.html"));
        assertTrue(Files.readString(workspace.resolve("README.md")).contains("Generated from ModelVersion v3."));
        assertTrue(Files.readString(workspace.resolve("README.md")).contains("GenerationRun"));
        assertFalse(Files.readString(workspace.resolve("docker-compose.yml")).contains("CASEGen"));
        assertTrue(Files.readString(workspace.resolve("docker-compose.yml")).contains("POSTGRES_DB: ${DB_NAME}"));
        assertTrue(Files.readString(workspace.resolve(".env")).contains("DB_NAME=ventas_api"));
        assertTrue(Files.readString(workspace.resolve(".env")).contains("DB_USERNAME=ventas_api_user"));
        assertTrue(Files.readString(workspace.resolve(".env")).contains("APP_PORT=8080"));
        assertTrue(Files.readString(workspace.resolve(".gitignore")).lines().anyMatch(".env"::equals));
        assertTrue(Files.readString(workspace.resolve("run.ps1")).contains("$env:SPRING_DATASOURCE_USERNAME = $env:DB_USERNAME"));
        assertTrue(Files.readString(workspace.resolve("run.sh")).contains("SPRING_DATASOURCE_USERNAME=\"${DB_USERNAME}\""));
        assertTrue(Files.exists(workspace.resolve("Dockerfile")));
        String compose=Files.readString(workspace.resolve("docker-compose.yml"));
        assertTrue(compose.contains("app:"));assertTrue(compose.contains("condition: service_healthy"));
        assertTrue(compose.contains("jdbc:postgresql://postgres:5432/${DB_NAME}"));
        assertTrue(compose.contains("${APP_PORT:-8080}:8080"));
        assertFalse(compose.contains("${DB_PORT:-5432}:5432"));
        String application=Files.readString(workspace.resolve("src/main/resources/application.yml"));
        assertTrue(application.contains("${SPRING_DATASOURCE_USERNAME}"));
        assertFalse(application.contains("${DB_USERNAME}"));
        Path collection=workspace.resolve("postman/ventas-api.postman_collection.json");var postman=json.readTree(Files.readString(collection));
        assertEquals("http://localhost:8080",postman.path("variable").get(0).path("value").asText());assertEquals(5,postman.path("item").get(0).path("item").size());
        assertTrue(Files.readString(collection).contains("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void pureJoinTableBecomesManyToManyWithoutIndependentCrud(@TempDir Path workspace)throws Exception {
        Table product=entity("product"),category=entity("category");
        Table bridge=joinTable(false);

        render(workspace,List.of(product,category,bridge));

        String schema=Files.readString(workspace.resolve("src/main/resources/db/migration/V1__create_schema.sql"));
        assertTrue(schema.contains("CREATE TABLE product_category"));
        assertTrue(schema.contains("PRIMARY KEY (product_id,category_id)"));
        assertTrue(schema.contains("FOREIGN KEY (product_id) REFERENCES product (id)"));
        assertTrue(schema.contains("FOREIGN KEY (category_id) REFERENCES category (id)"));
        String productEntity=generated(workspace,"entity/Product.java");
        String categoryEntity=generated(workspace,"entity/Category.java");
        assertTrue(productEntity.contains("@ManyToMany @JoinTable(name=\"product_category\""));
        assertTrue(productEntity.contains("joinColumns=@JoinColumn(name=\"product_id\",referencedColumnName=\"id\")"));
        assertTrue(productEntity.contains("inverseJoinColumns=@JoinColumn(name=\"category_id\",referencedColumnName=\"id\")"));
        assertTrue(categoryEntity.contains("@ManyToMany(mappedBy=\"categories\")"));
        assertFalse(Files.exists(javaRoot(workspace).resolve("entity/ProductCategory.java")));
        assertFalse(Files.exists(javaRoot(workspace).resolve("repository/ProductCategoryRepository.java")));
        assertFalse(Files.exists(javaRoot(workspace).resolve("service/ProductCategoryService.java")));
        assertFalse(Files.exists(javaRoot(workspace).resolve("controller/ProductCategoryController.java")));
        assertFalse(Files.exists(javaRoot(workspace).resolve("dto/ProductCategoryRequest.java")));
        assertFalse(Files.readString(workspace.resolve("postman/test-api.postman_collection.json")).contains("ProductCategory"));
    }

    @Test
    void joinTableWithBusinessColumnBecomesAssociationEntityWithEmbeddedId(@TempDir Path workspace)throws Exception {
        render(workspace,List.of(entity("product"),entity("category"),joinTable(true)));

        String entity=generated(workspace,"entity/ProductCategory.java");
        String id=generated(workspace,"entity/ProductCategoryId.java");
        String repository=generated(workspace,"repository/ProductCategoryRepository.java");
        assertTrue(entity.contains("@EmbeddedId private ProductCategoryId id"));
        assertTrue(entity.contains("@Column(name=\"assigned_at\""));
        assertTrue(id.contains("@Embeddable public class ProductCategoryId implements Serializable"));
        assertTrue(id.contains("productId"));
        assertTrue(id.contains("categoryId"));
        assertTrue(id.contains("equals(Object other)"));
        assertTrue(id.contains("hashCode()"));
        assertTrue(repository.contains("JpaRepository<ProductCategory,ProductCategoryId>"));
    }

    @Test
    void generatedArtifactNeverCopiesCasegenHostCredentials(@TempDir Path workspace)throws Exception {
        String previous=System.getProperty("DB_USERNAME");System.setProperty("DB_USERNAME","casegen_user");
        try {
            var profile=json.valueToTree(Map.of("profile",Map.of("basePackage","com.example.commercial","artifactId","sistema-comercial-api","groupId","com.example","javaVersion",21,"springBootVersion","4.1.1")));
            var input=new Response(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),profile,"VALID","checksum",UUID.randomUUID(),Instant.now(),List.of(entity("product")),List.of());
            new SpringBackendRenderer().render(workspace,input,new SpringBackendRenderer.Traceability(UUID.randomUUID(),UUID.randomUUID(),"Comercial",input.modelVersionId(),1,input.id()));
            String environment=Files.readString(workspace.resolve(".env"));
            assertTrue(environment.contains("DB_NAME=sistema_comercial_api"));
            assertTrue(environment.contains("DB_USERNAME=sistema_comercial_api_user"));
            assertTrue(environment.contains("DB_PASSWORD=dev_"));
            assertFalse(environment.contains("casegen_user"));
            assertFalse(Files.readString(workspace.resolve("docker-compose.yml")).contains("casegen_user"));
        } finally {if(previous==null)System.clearProperty("DB_USERNAME");else System.setProperty("DB_USERNAME",previous);}
    }

    private Table entity(String name){return new Table(name,UUID.randomUUID().toString(),null,List.of(
            new Column("id",UUID.randomUUID().toString(),"UUID",false,1),
            new Column("name",UUID.randomUUID().toString(),"VARCHAR(255)",false,2)),
            new Key("pk_"+name,List.of("id"),null),List.of(),List.of(),List.of(),List.of());}
    private Table joinTable(boolean businessColumn){List<Column> columns=new ArrayList<>(List.of(
            new Column("product_id",null,"UUID",false,1),new Column("category_id",null,"UUID",false,2)));
        if(businessColumn)columns.add(new Column("assigned_at",null,"VARCHAR(255)",false,3));
        return new Table("product_category",null,UUID.randomUUID().toString(),columns,
            new Key("pk_product_category",List.of("product_id","category_id"),null),List.of(
                new ForeignKey("fk_pc_product","product",List.of(new ColumnPair("product_id","id")),"CASCADE",null),
                new ForeignKey("fk_pc_category","category",List.of(new ColumnPair("category_id","id")),"CASCADE",null)),
            List.of(),List.of(),List.of());}
    private void render(Path workspace,List<Table> tables)throws Exception{var profile=json.valueToTree(Map.of("profile",Map.of(
            "basePackage","com.example.test","artifactId","test-api","groupId","com.example","javaVersion",21,"springBootVersion","4.1.1")));
        var input=new Response(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),profile,"VALID","checksum",UUID.randomUUID(),Instant.now(),tables,List.of());
        new SpringBackendRenderer().render(workspace,input,new SpringBackendRenderer.Traceability(
                UUID.randomUUID(),UUID.randomUUID(),"Test",input.modelVersionId(),1,input.id()));}
    private Path javaRoot(Path workspace){return workspace.resolve("src/main/java/com/example/test");}
    private String generated(Path workspace,String relative)throws Exception{return Files.readString(javaRoot(workspace).resolve(relative));}
}
