package com.casegen.ai.proposal.context;

import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.UmlElementType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record AiProposalContext(UUID projectId, UUID modelId, long modelRevision,
        UUID diagramId, Long diagramRevision, List<PackageEntry> packages,
        List<ClassifierEntry> classifiers, List<PropertyEntry> properties,
        List<OperationEntry> operations, List<RelationshipEntry> relationships) {

    public AiProposalContext {
        packages = List.copyOf(packages);
        classifiers = List.copyOf(classifiers);
        properties = List.copyOf(properties);
        operations = List.copyOf(operations);
        relationships = List.copyOf(relationships);
    }

    public Optional<PackageEntry> findPackage(UUID id) {
        return packages.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public Optional<ClassifierEntry> findClassifier(UUID id) {
        return classifiers.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public record PackageEntry(UUID id, String name, UUID parentPackageId, boolean visible) {
    }

    public record ClassifierEntry(UUID id, String name, ClassifierKind kind,
            UUID packageId, boolean visible) {
    }

    public record PropertyEntry(UUID id, UUID ownerClassifierId, String name, boolean visible) {
    }

    public record OperationEntry(UUID id, UUID ownerClassifierId, String name, boolean visible) {
    }

    public record RelationshipEntry(UUID id, UmlElementType type, UUID firstClassifierId,
            UUID secondClassifierId, boolean visible) {
    }
}
