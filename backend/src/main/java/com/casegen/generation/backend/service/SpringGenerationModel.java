package com.casegen.generation.backend.service;

import com.casegen.generation.logical.dto.LogicalModelDtos.ColumnPair;
import com.casegen.generation.logical.dto.LogicalModelDtos.ForeignKey;
import com.casegen.generation.logical.dto.LogicalModelDtos.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/** Immutable, resolved input consumed by deterministic templates. */
public final class SpringGenerationModel {
    public enum TableKind { STANDARD_ENTITY, PURE_JOIN_TABLE, ASSOCIATION_ENTITY }

    public record ClassifiedTable(Table table, TableKind kind) {}

    public record ManyToMany(Table joinTable, ForeignKey ownerForeignKey, ForeignKey inverseForeignKey,
            String ownerField, String inverseField) {
        public String ownerTable() { return ownerForeignKey.referencedTable(); }
        public String inverseTable() { return inverseForeignKey.referencedTable(); }
    }

    private final JsonNode profile;
    private final List<Table> tables;
    private final List<ClassifiedTable> classifiedTables;
    private final List<ManyToMany> manyToMany;
    private final Map<String, TableKind> kindsByName;

    public SpringGenerationModel(JsonNode profile, List<Table> tables) {
        this.profile = profile;
        this.tables = List.copyOf(tables);
        this.classifiedTables = this.tables.stream().map(table -> new ClassifiedTable(table, classify(table))).toList();
        this.kindsByName = classifiedTables.stream().collect(Collectors.toUnmodifiableMap(
                classified -> classified.table().name(), ClassifiedTable::kind));
        this.manyToMany = classifiedTables.stream()
                .filter(classified -> classified.kind() == TableKind.PURE_JOIN_TABLE)
                .map(classified -> relationship(classified.table()))
                .toList();
    }

    public JsonNode profile() { return profile; }
    public List<Table> tables() { return tables; }
    public List<ClassifiedTable> classifiedTables() { return classifiedTables; }
    public List<ManyToMany> manyToMany() { return manyToMany; }
    public TableKind kind(Table table) { return kindsByName.get(table.name()); }
    public List<Table> generatedEntities() {
        return classifiedTables.stream().filter(table -> table.kind() != TableKind.PURE_JOIN_TABLE)
                .map(ClassifiedTable::table).toList();
    }
    public List<ManyToMany> relationshipsFor(String tableName) {
        return manyToMany.stream().filter(relation -> relation.ownerTable().equals(tableName)
                || relation.inverseTable().equals(tableName)).toList();
    }

    private static TableKind classify(Table table) {
        if (table.primaryKey() == null || table.primaryKey().columns() == null) return TableKind.STANDARD_ENTITY;
        Set<String> primaryKey = new LinkedHashSet<>(table.primaryKey().columns());
        Set<String> foreignKeyColumns = table.foreignKeys().stream().flatMap(foreignKey -> foreignKey.columns().stream())
                .map(ColumnPair::column).collect(Collectors.toCollection(LinkedHashSet::new));
        boolean twoPrimaryForeignKeys = table.foreignKeys().size() == 2
                && table.foreignKeys().stream().allMatch(foreignKey -> !foreignKey.columns().isEmpty()
                    && foreignKey.columns().stream().allMatch(pair -> primaryKey.contains(pair.column())))
                && primaryKey.equals(foreignKeyColumns);
        Set<String> allColumns = table.columns().stream().map(column -> column.name())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (twoPrimaryForeignKeys && allColumns.equals(foreignKeyColumns)) return TableKind.PURE_JOIN_TABLE;
        if (twoPrimaryForeignKeys || primaryKey.size() > 1 || table.sourceAssociationId() != null) {
            return TableKind.ASSOCIATION_ENTITY;
        }
        return TableKind.STANDARD_ENTITY;
    }

    private static ManyToMany relationship(Table joinTable) {
        Map<String, Integer> primaryOrder = java.util.stream.IntStream.range(0, joinTable.primaryKey().columns().size())
                .boxed().collect(Collectors.toMap(index -> joinTable.primaryKey().columns().get(index), Function.identity()));
        List<ForeignKey> ordered = new ArrayList<>(joinTable.foreignKeys());
        ordered.sort(Comparator.comparingInt(foreignKey -> primaryOrder.getOrDefault(
                foreignKey.columns().getFirst().column(), Integer.MAX_VALUE)));
        ForeignKey owner = ordered.get(0), inverse = ordered.get(1);
        String ownerField = collectionField(inverse.referencedTable());
        String inverseField = collectionField(owner.referencedTable());
        if (owner.referencedTable().equals(inverse.referencedTable())) {
            ownerField += "By" + javaName(inverse.columns().getFirst().column());
            inverseField += "By" + javaName(owner.columns().getFirst().column());
        }
        return new ManyToMany(joinTable, owner, inverse, ownerField, inverseField);
    }

    private static String collectionField(String table) {
        String singular = field(table);
        if (singular.endsWith("y") && singular.length() > 1) return singular.substring(0, singular.length() - 1) + "ies";
        if (singular.endsWith("s")) return singular + "es";
        return singular + "s";
    }
    private static String javaName(String value) {
        StringBuilder result = new StringBuilder();
        for (String part : value.split("[-_]")) {
            if (!part.isBlank()) result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
    private static String field(String value) {
        String name = javaName(value);
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
