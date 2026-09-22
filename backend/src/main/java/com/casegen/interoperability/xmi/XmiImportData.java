package com.casegen.interoperability.xmi;

import java.util.List;
import java.util.Map;

public record XmiImportData(
        String modelName,
        String modelExternalId,
        List<PackageData> packages,
        List<ClassifierData> classifiers,
        List<PropertyData> properties,
        List<OperationData> operations,
        List<ParameterData> parameters,
        List<AssociationData> associations,
        List<GeneralizationData> generalizations,
        List<RealizationData> realizations,
        List<XmiImportWarning> warnings) {

    public record PackageData(String externalId, String name, String parentExternalId) {}

    public record ClassifierData(String externalId, String name, String packageExternalId,
            String kind, String visibility, boolean abstractClassifier, boolean active) {}

    public record TypedValue(String typeKind, String primitiveType, String classifierExternalId) {}

    public record PropertyData(String externalId, String name, String ownerClassifierExternalId,
            String visibility, TypedValue type, int lower, Integer upper,
            boolean staticProperty, boolean readOnly, boolean derived) {}

    public record OperationData(String externalId, String name, String ownerClassifierExternalId,
            String visibility, boolean abstractOperation, boolean staticOperation, boolean query) {}

    public record ParameterData(String externalId, String name, String operationExternalId,
            String direction, TypedValue type, int lower, Integer upper, int position) {}

    public record AssociationData(String externalId, String name, String packageExternalId,
            AssociationEndData first, AssociationEndData second) {}

    public record AssociationEndData(String externalId, String roleName, String classifierExternalId,
            String visibility, int lower, Integer upper, boolean navigable,
            String aggregation, String ownership) {}

    public record GeneralizationData(String externalId, String specificExternalId,
            String generalExternalId, boolean substitutable) {}

    public record RealizationData(String externalId, String clientExternalId,
            String supplierExternalId) {}

    public record XmiImportWarning(String code, String message, String externalId,
            Map<String, Object> metadata) {
        public XmiImportWarning(String code, String message, String externalId) {
            this(code, message, externalId, Map.of());
        }

        public XmiImportWarning {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
