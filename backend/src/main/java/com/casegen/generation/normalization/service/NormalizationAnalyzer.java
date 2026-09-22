package com.casegen.generation.normalization.service;

import com.casegen.generation.logical.dto.LogicalModelDtos.Column;
import com.casegen.generation.logical.dto.LogicalModelDtos.ForeignKey;
import com.casegen.generation.logical.dto.LogicalModelDtos.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class NormalizationAnalyzer {
    public record Issue(String tableName, String columnName, List<String> columns,
            String normalForm, String severity, String code, String message,
            String recommendation, String sourceElementId, JsonNode metadata) {}
    public record Result(String status, String highestNormalForm, List<Issue> issues) {}

    private final JsonMapper json = JsonMapper.builder().build();

    public Result analyze(List<Table> tables) {
        if (tables.isEmpty()) return new Result("UNKNOWN", null, List.of());
        Map<String, Table> byName = new LinkedHashMap<>();
        tables.stream().sorted(java.util.Comparator.comparing(Table::name)).forEach(table -> byName.put(table.name(), table));
        List<Issue> issues = new ArrayList<>();
        for (Table table : byName.values()) {
            analyzeFirstNormalForm(table, issues);
            analyzeKnownDependencies(table, byName, issues);
        }
        issues.sort(java.util.Comparator.comparing(Issue::normalForm).thenComparing(Issue::tableName)
                .thenComparing(Issue::code).thenComparing(issue -> String.join(",", issue.columns())));
        boolean errors = issues.stream().anyMatch(issue -> issue.severity().equals("ERROR"));
        String status = errors ? "FAIL" : issues.isEmpty() ? "PASS" : "WARNING";
        String highest = issues.stream().anyMatch(issue -> issue.normalForm().equals("1NF")) ? null
                : issues.stream().anyMatch(issue -> issue.normalForm().equals("2NF")) ? "1NF"
                : issues.stream().anyMatch(issue -> issue.normalForm().equals("3NF")) ? "2NF" : "3NF";
        return new Result(status, highest, List.copyOf(issues));
    }

    private void analyzeFirstNormalForm(Table table, List<Issue> issues) {
        for (Column column : table.columns()) {
            String type = column.sqlType().toUpperCase(Locale.ROOT);
            if (type.equals("JSON") || type.equals("JSONB") || type.contains("ARRAY") || type.endsWith("[]")) {
                issues.add(issue(table, column, List.of(column.name()), "1NF", "ERROR", "NON_ATOMIC_COLUMN_TYPE",
                        "La columna " + table.name() + "." + column.name() + " usa " + column.sqlType()
                                + ", que puede contener varios valores o una estructura no atómica.",
                        "Extrae sus valores a columnas atómicas o a una tabla relacionada.", column.sourcePropertyId(),
                        Map.of("sqlType", column.sqlType(), "dependencyConfidence", "KNOWN")));
            }
        }
        Map<String, List<Column>> repeating = new LinkedHashMap<>();
        for (Column column : table.columns()) {
            String base = column.name().replaceFirst("_[0-9]+$", "");
            if (!base.equals(column.name())) repeating.computeIfAbsent(base, ignored -> new ArrayList<>()).add(column);
        }
        repeating.forEach((base, columns) -> {
            if (columns.size() > 1) issues.add(issue(table, columns.getFirst(), columns.stream().map(Column::name).toList(),
                    "1NF", "ERROR", "REPEATING_COLUMN_GROUP",
                    "La tabla " + table.name() + " contiene el grupo repetitivo "
                            + String.join(", ", columns.stream().map(Column::name).toList()) + ".",
                    "Crea una tabla hija con una fila por valor de " + base + ".",
                    source(columns.getFirst(), table), Map.of("group", base, "dependencyConfidence", "KNOWN")));
        });
        if (table.sourceAssociationId() != null) {
            Set<String> key = table.primaryKey() == null ? Set.of() : new LinkedHashSet<>(table.primaryKey().columns());
            Set<String> foreignColumns = new LinkedHashSet<>();
            table.foreignKeys().forEach(fk -> fk.columns().forEach(pair -> foreignColumns.add(pair.column())));
            if (table.foreignKeys().size() != 2 || !key.equals(foreignColumns)) {
                issues.add(issue(table, null, new ArrayList<>(foreignColumns), "1NF", "ERROR", "INVALID_MANY_TO_MANY_BRIDGE",
                        "La tabla derivada de la asociación " + table.name()
                                + " no conserva dos claves foráneas formando su clave primaria.",
                        "Representa la relación N:M mediante una tabla puente cuya PK esté formada por las dos FK.",
                        table.sourceAssociationId(), Map.of("primaryKeyColumns", key, "foreignKeyColumns", foreignColumns,
                                "dependencyConfidence", "KNOWN")));
            }
        }
    }

    private void analyzeKnownDependencies(Table table, Map<String, Table> byName, List<Issue> issues) {
        if (table.primaryKey() == null) return;
        Set<String> primaryKey = new LinkedHashSet<>(table.primaryKey().columns());
        for (ForeignKey foreignKey : table.foreignKeys()) {
            Table referenced = byName.get(foreignKey.referencedTable());
            if (referenced == null || referenced.primaryKey() == null) continue;
            Set<String> determinant = new LinkedHashSet<>(foreignKey.columns().stream().map(pair -> pair.column()).toList());
            for (Column dependent : duplicatedColumns(table, primaryKey, foreignKey, referenced)) {
                boolean partial = primaryKey.size() > 1 && primaryKey.containsAll(determinant)
                        && determinant.size() < primaryKey.size();
                String form = partial ? "2NF" : "3NF";
                if (!partial && primaryKey.equals(determinant)) continue;
                String dependency = String.join(", ", determinant) + " -> " + dependent.name();
                issues.add(issue(table, dependent, concat(determinant, dependent.name()), form, "WARNING",
                        partial ? "KNOWN_PARTIAL_DEPENDENCY" : "KNOWN_TRANSITIVE_DEPENDENCY",
                        "En " + table.name() + " se infiere la dependencia " + dependency
                                + " mediante la FK hacia " + referenced.name() + ".",
                        "Conserva " + dependent.name() + " en " + referenced.name()
                                + " y obtén su valor mediante la relación, evitando duplicarlo en " + table.name() + ".",
                        source(dependent, table), Map.of("determinantColumns", determinant,
                                "dependentColumns", List.of(dependent.name()), "referencedTable", referenced.name(),
                                "dependency", dependency, "dependencyConfidence", "INFERRED")));
            }
        }
    }

    private List<Column> duplicatedColumns(Table table, Set<String> primaryKey, ForeignKey foreignKey, Table referenced) {
        Set<String> foreignColumns = new LinkedHashSet<>();
        table.foreignKeys().forEach(fk -> fk.columns().forEach(pair -> foreignColumns.add(pair.column())));
        Set<String> referencedPrimary = new LinkedHashSet<>(referenced.primaryKey().columns());
        List<Column> referencedValues = referenced.columns().stream()
                .filter(column -> !referencedPrimary.contains(column.name())).toList();
        List<Column> result = new ArrayList<>();
        for (Column local : table.columns()) {
            if (primaryKey.contains(local.name()) || foreignColumns.contains(local.name())) continue;
            String expectedPrefix = referenced.name() + "_";
            boolean sameTrace = local.sourcePropertyId() != null && referencedValues.stream()
                    .anyMatch(target -> local.sourcePropertyId().equals(target.sourcePropertyId()));
            boolean conventionalCopy = local.name().startsWith(expectedPrefix) && referencedValues.stream()
                    .anyMatch(target -> local.name().substring(expectedPrefix.length()).equals(target.name()));
            if (sameTrace || conventionalCopy) result.add(local);
        }
        return result;
    }

    private Issue issue(Table table, Column column, List<String> columns, String form, String severity,
            String code, String message, String recommendation, String sourceElementId, Map<String, ?> metadata) {
        return new Issue(table.name(), column == null ? null : column.name(), List.copyOf(columns), form, severity,
                code, message, recommendation, sourceElementId, json.valueToTree(metadata));
    }
    private String source(Column column, Table table) {
        return column != null && column.sourcePropertyId() != null ? column.sourcePropertyId()
                : table.sourceClassifierId() != null ? table.sourceClassifierId() : table.sourceAssociationId();
    }
    private List<String> concat(Set<String> determinant, String dependent) {
        List<String> result = new ArrayList<>(determinant); result.add(dependent); return result;
    }
}
