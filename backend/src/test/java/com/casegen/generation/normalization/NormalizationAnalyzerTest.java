package com.casegen.generation.normalization;

import static com.casegen.generation.logical.dto.LogicalModelDtos.*;
import static org.junit.jupiter.api.Assertions.*;

import com.casegen.generation.normalization.service.NormalizationAnalyzer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NormalizationAnalyzerTest {
    private final NormalizationAnalyzer analyzer = new NormalizationAnalyzer();

    @Test
    void normalizedCustomerAndOrderPassKnownDependenciesThroughThirdNormalForm() {
        Table customer = table("customer", List.of(column("id", "UUID"), column("name", "VARCHAR(255)")), List.of("id"), List.of());
        Table order = table("purchase_order", List.of(column("id", "UUID"), column("customer_id", "UUID")), List.of("id"),
                List.of(foreign("customer_id", "customer")));
        var result = analyzer.analyze(List.of(customer, order));
        assertEquals("PASS", result.status()); assertEquals("3NF", result.highestNormalForm()); assertTrue(result.issues().isEmpty());
    }

    @Test
    void detectsNonAtomicAndRepeatingAttributesAsFirstNormalFormErrors() {
        Table table = table("customer", List.of(column("id", "UUID"), column("phones", "TEXT[]"),
                column("email_1", "VARCHAR(255)"), column("email_2", "VARCHAR(255)")), List.of("id"), List.of());
        var result = analyzer.analyze(List.of(table));
        assertEquals("FAIL", result.status()); assertNull(result.highestNormalForm());
        assertEquals(List.of("NON_ATOMIC_COLUMN_TYPE", "REPEATING_COLUMN_GROUP"),
                result.issues().stream().map(NormalizationAnalyzer.Issue::code).sorted().toList());
    }

    @Test
    void detectsKnownPartialDependencyForCompositePrimaryKey() {
        String categoryName = UUID.randomUUID().toString();
        Table category = table("category", List.of(column("id", "UUID"), column("name", "VARCHAR(255)", categoryName)), List.of("id"), List.of());
        Table bridge = table("product_category", List.of(column("product_id", "UUID"), column("category_id", "UUID"),
                column("category_name", "VARCHAR(255)", categoryName)), List.of("product_id", "category_id"),
                List.of(foreign("product_id", "product"), foreign("category_id", "category")));
        Table product = table("product", List.of(column("id", "UUID")), List.of("id"), List.of());
        var result = analyzer.analyze(List.of(product, category, bridge));
        assertEquals("WARNING", result.status()); assertEquals("1NF", result.highestNormalForm());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("KNOWN_PARTIAL_DEPENDENCY")
                && issue.normalForm().equals("2NF") && issue.columns().contains("category_name")));
    }

    @Test
    void detectsKnownTransitiveDependencyAndIsDeterministic() {
        String customerName = UUID.randomUUID().toString();
        Table customer = table("customer", List.of(column("id", "UUID"), column("name", "VARCHAR(255)", customerName)), List.of("id"), List.of());
        Table order = table("purchase_order", List.of(column("id", "UUID"), column("customer_id", "UUID"),
                column("customer_name", "VARCHAR(255)", customerName)), List.of("id"), List.of(foreign("customer_id", "customer")));
        var first = analyzer.analyze(List.of(order, customer));
        var second = analyzer.analyze(List.of(order, customer));
        assertEquals("WARNING", first.status()); assertEquals("2NF", first.highestNormalForm());
        assertEquals(first, second);
        assertTrue(first.issues().stream().anyMatch(issue -> issue.code().equals("KNOWN_TRANSITIVE_DEPENDENCY")
                && issue.metadata().path("dependency").asText().equals("customer_id -> customer_name")));
    }

    private Table table(String name, List<Column> columns, List<String> primaryKey, List<ForeignKey> foreignKeys) {
        return new Table(name, UUID.randomUUID().toString(), null, columns,
                new Key("pk_" + name, primaryKey, null), foreignKeys, List.of(), List.of(), List.of());
    }
    private Column column(String name, String type) { return column(name, type, UUID.randomUUID().toString()); }
    private Column column(String name, String type, String source) { return new Column(name, source, type, false, 1); }
    private ForeignKey foreign(String column, String target) {
        return new ForeignKey("fk_" + column, target, List.of(new ColumnPair(column, "id")), "NO ACTION", UUID.randomUUID().toString());
    }
}
