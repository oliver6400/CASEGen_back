package com.casegen.interoperability.xmi;

import static com.casegen.interoperability.xmi.XmiImportData.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Creates the initial notation projection for an imported semantic UML model. */
@Service
public class ImportedClassDiagramCreator {
    static final double NODE_WIDTH = 310;
    static final double NODE_HEIGHT = 180;
    static final double HORIZONTAL_GAP = 380;
    static final double VERTICAL_GAP = 250;
    static final int MAX_COLUMNS = 5;

    private final JdbcTemplate jdbc;

    public ImportedClassDiagramCreator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CreationResult create(UUID modelId, String modelName, UUID createdBy,
            XmiImportData data, Map<String, UUID> semanticIds, Instant now) {
        if (data.classifiers().isEmpty()) return CreationResult.notCreated();

        UUID diagramId = UUID.randomUUID();
        String diagramName = importedDiagramName(modelName);
        UUID ownerPackageId = rootPackageId(data, semanticIds);
        List<NodePlacement> placements = layout(data);
        List<RelationshipPlacement> relationships = relationships(data);
        long revision = placements.size() + relationships.size();
        Timestamp timestamp = Timestamp.from(now);

        jdbc.update("""
                INSERT INTO diagrams(id,model_id,owner_package_id,name,type,revision,lock_version,created_by,created_at,updated_at)
                VALUES(?,?,?,?,'CLASS',?,0,?,?,?)
                """, diagramId, modelId, ownerPackageId, diagramName, revision, createdBy, timestamp, timestamp);

        Map<String, DiagramNodeRef> nodes = new HashMap<>();
        for (NodePlacement placement : placements) {
            UUID nodeId = UUID.randomUUID();
            UUID classifierId = semanticIds.get(placement.classifierExternalId());
            jdbc.update("""
                    INSERT INTO diagram_nodes(id,diagram_id,uml_element_id,x,y,width,height,z_index,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?)
                    """, nodeId, diagramId, classifierId, placement.x(), placement.y(),
                    NODE_WIDTH, NODE_HEIGHT, placement.zIndex(), timestamp, timestamp);
            nodes.put(placement.classifierExternalId(),
                    new DiagramNodeRef(nodeId, placement.x(), placement.y()));
        }

        Map<AnchorKey, Integer> anchors = new HashMap<>();
        for (RelationshipPlacement relationship : relationships) {
            DiagramNodeRef source = nodes.get(relationship.sourceClassifierExternalId());
            DiagramNodeRef target = nodes.get(relationship.targetClassifierExternalId());
            if (source == null || target == null) continue;
            Sides sides = chooseSides(source, target);
            int sourceAnchor = nextAnchor(anchors, source.id(), sides.source());
            int targetAnchor = nextAnchor(anchors, target.id(), sides.target());
            jdbc.update("""
                    INSERT INTO diagram_edges(id,diagram_id,uml_element_id,source_node_id,target_node_id,
                        source_side,target_side,source_anchor_index,target_anchor_index,waypoints_json,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?)
                    """, UUID.randomUUID(), diagramId, semanticIds.get(relationship.elementExternalId()),
                    source.id(), target.id(), sides.source(), sides.target(), sourceAnchor, targetAnchor,
                    "[]", timestamp, timestamp);
        }
        return new CreationResult(diagramId, diagramName, placements.size(), relationships.size(), true);
    }

    List<NodePlacement> layout(XmiImportData data) {
        List<ClassifierData> ordered = data.classifiers().stream()
                .sorted(Comparator.comparing((ClassifierData value) -> value.name().toLowerCase(Locale.ROOT))
                        .thenComparing(ClassifierData::externalId))
                .toList();
        Map<String, Integer> ranks = new HashMap<>();
        ordered.forEach(classifier -> ranks.put(classifier.externalId(), 0));

        List<RankConstraint> constraints = new ArrayList<>();
        data.generalizations().forEach(relation -> constraints.add(
                new RankConstraint(relation.generalExternalId(), relation.specificExternalId())));
        data.realizations().forEach(relation -> constraints.add(
                new RankConstraint(relation.supplierExternalId(), relation.clientExternalId())));
        data.associations().forEach(relation -> {
            AssociationEndData whole = whole(relation);
            if (whole == null) return;
            AssociationEndData part = whole == relation.first() ? relation.second() : relation.first();
            if (!whole.classifierExternalId().equals(part.classifierExternalId())) {
                constraints.add(new RankConstraint(whole.classifierExternalId(), part.classifierExternalId()));
            }
        });
        for (int pass = 0; pass < ordered.size(); pass++) {
            boolean changed = false;
            for (RankConstraint constraint : constraints) {
                Integer parentRank = ranks.get(constraint.before());
                Integer childRank = ranks.get(constraint.after());
                if (parentRank != null && childRank != null && childRank <= parentRank) {
                    ranks.put(constraint.after(), parentRank + 1);
                    changed = true;
                }
            }
            if (!changed) break;
        }

        List<NodePlacement> result = new ArrayList<>();
        int rowCursor = 0;
        int zIndex = 0;
        int maximumRank = ranks.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int rank = 0; rank <= maximumRank; rank++) {
            int currentRank = rank;
            List<ClassifierData> layer = ordered.stream()
                    .filter(classifier -> ranks.get(classifier.externalId()) == currentRank).toList();
            for (int index = 0; index < layer.size(); index++) {
                int column = index % MAX_COLUMNS;
                int row = rowCursor + index / MAX_COLUMNS;
                result.add(new NodePlacement(layer.get(index).externalId(),
                        80 + column * HORIZONTAL_GAP, 80 + row * VERTICAL_GAP, zIndex++));
            }
            if (!layer.isEmpty()) rowCursor += Math.max(1, (layer.size() + MAX_COLUMNS - 1) / MAX_COLUMNS);
        }
        return List.copyOf(result);
    }

    private List<RelationshipPlacement> relationships(XmiImportData data) {
        List<RelationshipPlacement> result = new ArrayList<>();
        data.associations().forEach(relation -> result.add(new RelationshipPlacement(
                relation.externalId(), relation.first().classifierExternalId(),
                relation.second().classifierExternalId())));
        data.generalizations().forEach(relation -> result.add(new RelationshipPlacement(
                relation.externalId(), relation.specificExternalId(), relation.generalExternalId())));
        data.realizations().forEach(relation -> result.add(new RelationshipPlacement(
                relation.externalId(), relation.clientExternalId(), relation.supplierExternalId())));
        return List.copyOf(result);
    }

    private AssociationEndData whole(AssociationData association) {
        if (isWhole(association.first())) return association.first();
        if (isWhole(association.second())) return association.second();
        return null;
    }

    private boolean isWhole(AssociationEndData end) {
        return "SHARED".equals(end.aggregation()) || "COMPOSITE".equals(end.aggregation());
    }

    private UUID rootPackageId(XmiImportData data, Map<String, UUID> semanticIds) {
        return data.packages().stream().filter(value -> value.parentExternalId() == null)
                .sorted(Comparator.comparing((PackageData value) -> value.name().toLowerCase(Locale.ROOT))
                        .thenComparing(PackageData::externalId))
                .map(PackageData::externalId).map(semanticIds::get).findFirst().orElse(null);
    }

    private String importedDiagramName(String modelName) {
        String suffix = " - Importado";
        String base = modelName.trim();
        if (base.length() + suffix.length() > 180) base = base.substring(0, 180 - suffix.length()).trim();
        return base + suffix;
    }

    private Sides chooseSides(DiagramNodeRef source, DiagramNodeRef target) {
        if (source.id().equals(target.id())) return new Sides("RIGHT", "BOTTOM");
        double dx = target.x() - source.x();
        double dy = target.y() - source.y();
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? new Sides("RIGHT", "LEFT") : new Sides("LEFT", "RIGHT");
        }
        return dy >= 0 ? new Sides("BOTTOM", "TOP") : new Sides("TOP", "BOTTOM");
    }

    private int nextAnchor(Map<AnchorKey, Integer> anchors, UUID nodeId, String side) {
        AnchorKey key = new AnchorKey(nodeId, side);
        int result = anchors.getOrDefault(key, 0);
        anchors.put(key, result + 1);
        return result;
    }

    public record CreationResult(UUID diagramId, String diagramName,
            int nodeCount, int edgeCount, boolean created) {
        static CreationResult notCreated() { return new CreationResult(null, null, 0, 0, false); }
    }

    record NodePlacement(String classifierExternalId, double x, double y, int zIndex) {}
    private record RelationshipPlacement(String elementExternalId,
            String sourceClassifierExternalId, String targetClassifierExternalId) {}
    private record RankConstraint(String before, String after) {}
    private record DiagramNodeRef(UUID id, double x, double y) {}
    private record Sides(String source, String target) {}
    private record AnchorKey(UUID nodeId, String side) {}
}
