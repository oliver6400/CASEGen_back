package com.casegen.generation.logical.service;

import com.casegen.generation.logical.dto.LogicalModelDtos.Check;
import com.casegen.generation.logical.dto.LogicalModelDtos.ForeignKey;
import com.casegen.generation.logical.dto.LogicalModelDtos.Index;
import com.casegen.generation.logical.dto.LogicalModelDtos.Issue;
import com.casegen.generation.logical.dto.LogicalModelDtos.Key;
import com.casegen.generation.logical.dto.LogicalModelDtos.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Validates the unquoted identifiers emitted for the supported PostgreSQL dialect. */
public final class PostgresIdentifierValidator {

    private static final Set<String> RESERVED = Set.of(
            "all", "analyse", "analyze", "and", "any", "array", "as", "asc", "asymmetric",
            "authorization", "binary", "both", "case", "cast", "check", "collate", "collation",
            "column", "concurrently", "constraint", "create", "cross", "current_catalog",
            "current_date", "current_role", "current_schema", "current_time", "current_timestamp",
            "current_user", "default", "deferrable", "desc", "distinct", "do", "else", "end",
            "except", "false", "fetch", "for", "foreign", "freeze", "from", "full", "grant",
            "group", "having", "ilike", "in", "initially", "inner", "intersect", "into", "is",
            "isnull", "join", "lateral", "leading", "left", "like", "limit", "localtime",
            "localtimestamp", "natural", "not", "notnull", "null", "nulls", "offset", "on", "only",
            "or", "order", "outer", "overlaps", "placing", "primary", "references", "returning",
            "right", "select", "session_user", "similar", "some", "symmetric", "system_user", "table",
            "tablesample", "then", "to", "trailing", "true", "union", "unique", "user", "using",
            "variadic", "verbose", "when", "where", "window", "with");

    private PostgresIdentifierValidator() {
    }

    public static List<Issue> validate(List<Table> tables, Function<String, String> sourceName) {
        List<Issue> issues = new ArrayList<>();
        for (Table table : tables) {
            String tableSource = first(table.sourceClassifierId(), table.sourceAssociationId());
            validate(issues, table.name(), "TABLE", tableSource, sourceName);
            table.columns().forEach(column ->
                    validate(issues, column.name(), "COLUMN", column.sourcePropertyId(), sourceName));
            validateKey(issues, table.primaryKey(), "PRIMARY_KEY_CONSTRAINT", sourceName);
            table.uniqueConstraints().forEach(key ->
                    validateKey(issues, key, "UNIQUE_CONSTRAINT", sourceName));
            for (Check check : table.checks()) {
                validate(issues, check.name(), "CHECK_CONSTRAINT", check.sourceElementId(), sourceName);
            }
            for (Index index : table.indexes()) {
                validate(issues, index.name(), "INDEX", index.sourceElementId(), sourceName);
            }
            for (ForeignKey foreignKey : table.foreignKeys()) {
                validate(issues, foreignKey.name(), "FOREIGN_KEY_CONSTRAINT",
                        foreignKey.sourceElementId(), sourceName);
                validate(issues, foreignKey.referencedTable(), "REFERENCED_TABLE",
                        foreignKey.sourceElementId(), sourceName);
            }
        }
        return List.copyOf(issues);
    }

    public static boolean isReserved(String identifier) {
        return identifier != null && RESERVED.contains(identifier.toLowerCase(Locale.ROOT));
    }

    private static void validateKey(List<Issue> issues, Key key, String type,
            Function<String, String> sourceName) {
        if (key != null) validate(issues, key.name(), type, key.sourceElementId(), sourceName);
    }

    private static void validate(List<Issue> issues, String sqlName, String type, String source,
            Function<String, String> sourceName) {
        if (!isReserved(sqlName)) return;
        String element = source == null ? "generated" : safeSourceName(sourceName, source);
        String suggestion = sqlName.toLowerCase(Locale.ROOT) + suffix(type);
        issues.add(new Issue("ERROR", "POSTGRES_RESERVED_IDENTIFIER",
                "PostgreSQL reserved identifier: element=" + element + ", sqlName=" + sqlName
                        + ", identifierType=" + type + "; configure a safe alias such as " + suggestion,
                source, "/logical/identifiers"));
    }

    private static String safeSourceName(Function<String, String> sourceName, String source) {
        String name = sourceName.apply(source);
        return name == null || name.isBlank() ? source : name;
    }

    private static String suffix(String type) {
        if (type.contains("TABLE")) return "_table";
        if (type.equals("COLUMN")) return "_value";
        if (type.equals("INDEX")) return "_index";
        return "_constraint";
    }

    private static String first(String first, String second) {
        return first != null ? first : second;
    }
}
