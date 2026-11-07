package com.casegen.ai.proposal.review;

import com.casegen.modeling.domain.*;
import java.util.List;
import java.util.UUID;

public record AiUmlReviewSnapshot(UUID projectId, String projectName, UUID modelId,
        String modelName, long modelRevision, DiagramContext diagram, boolean truncated,
        Counts totalCounts, List<PackageEntry> packages, List<ClassifierEntry> classifiers,
        List<PropertyEntry> properties, List<OperationEntry> operations,
        List<AssociationEntry> associations, List<GeneralizationEntry> generalizations,
        List<RealizationEntry> realizations) {
    public AiUmlReviewSnapshot {
        packages=List.copyOf(packages); classifiers=List.copyOf(classifiers);
        properties=List.copyOf(properties); operations=List.copyOf(operations);
        associations=List.copyOf(associations); generalizations=List.copyOf(generalizations);
        realizations=List.copyOf(realizations);
    }
    public record Counts(int packages, int classifiers, int properties, int operations, int relationships) { }
    public record DiagramContext(UUID id, String name, long revision,
            List<UUID> visibleElementIds, List<UUID> visibleRelationshipIds) {
        public DiagramContext { visibleElementIds=List.copyOf(visibleElementIds); visibleRelationshipIds=List.copyOf(visibleRelationshipIds); }
    }
    public record PackageEntry(UUID id,String name,String documentation,UUID parentPackageId) { }
    public record ClassifierEntry(UUID id,String name,String documentation,UUID packageId,
            ClassifierKind kind,Visibility visibility,boolean abstractClassifier,boolean active,
            boolean visibleInCurrentDiagram) { }
    public record PropertyEntry(UUID id,UUID ownerClassifierId,String name,String documentation,
            Visibility visibility,TypeKind typeKind,PrimitiveType primitiveType,UUID typeClassifierId,
            int lowerBound,Integer upperBound,boolean staticProperty,boolean readOnly,boolean derived) { }
    public record OperationEntry(UUID id,UUID ownerClassifierId,String name,String documentation,
            Visibility visibility,boolean abstractOperation,boolean staticOperation,boolean query) { }
    public record AssociationEndEntry(UUID classifierId,String roleName,int lowerBound,Integer upperBound,
            boolean navigable,AggregationKind aggregation,AssociationEndOwnership ownership) { }
    public record AssociationEntry(UUID id,String name,String documentation,UUID packageId,
            AssociationEndEntry firstEnd,AssociationEndEntry secondEnd,boolean visibleInCurrentDiagram) { }
    public record GeneralizationEntry(UUID id,UUID specificClassifierId,UUID generalClassifierId,
            boolean substitutable,String documentation,boolean visibleInCurrentDiagram) { }
    public record RealizationEntry(UUID id,UUID clientClassifierId,UUID supplierClassifierId,
            String documentation,boolean visibleInCurrentDiagram) { }
}
