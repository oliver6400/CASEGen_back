package com.casegen.generation.logical.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class LogicalModelDtos {
    private LogicalModelDtos() {}
    public record BuildRequest(@NotNull UUID generationProfileId) {}
    public record Issue(String severity, String code, String message, String sourceElementId, String path) {}
    public record Column(String name, String sourcePropertyId, String sqlType, boolean nullable, int ordinalPosition) {}
    public record Key(String name, List<String> columns, String sourceElementId) {}
    public record ColumnPair(String column, String referencedColumn) {}
    public record ForeignKey(String name, String referencedTable, List<ColumnPair> columns, String onDelete, String sourceElementId) {}
    public record Index(String name, List<String> columns, boolean unique, String sourceElementId) {}
    public record Check(String name, String expression, String sourceElementId) {}
    public record Table(String name, String sourceClassifierId, String sourceAssociationId, List<Column> columns,
            Key primaryKey, List<ForeignKey> foreignKeys, List<Key> uniqueConstraints, List<Index> indexes, List<Check> checks) {}
    public record Plan(List<Table> tables, List<Issue> issues) {
        public boolean valid() { return issues.stream().noneMatch(i -> i.severity().equals("ERROR")); }
    }
    public record Response(UUID id, UUID modelVersionId, UUID generationProfileId, JsonNode profileSnapshot,
            String status, String checksum, UUID createdBy, Instant createdAt, List<Table> tables, List<Issue> issues) {}
    public record Summary(UUID id, UUID modelVersionId, long versionNumber, UUID generationProfileId,
            String profileName, String status, String checksum, Instant createdAt) {}
}
