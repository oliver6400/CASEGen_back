package com.casegen.interoperability.image;

import java.util.List;

/** A non-persistent, user-editable proposal extracted from an image. */
public record DetectedClassDiagram(
        String suggestedModelName,
        List<DetectedClassifier> detectedClassifiers,
        List<DetectedAttribute> detectedAttributes,
        List<DetectedOperation> detectedOperations,
        List<DetectedRelationship> detectedRelationships,
        List<DetectionWarning> warnings,
        double confidence) {

    public DetectedClassDiagram {
        detectedClassifiers = copy(detectedClassifiers);
        detectedAttributes = copy(detectedAttributes);
        detectedOperations = copy(detectedOperations);
        detectedRelationships = copy(detectedRelationships);
        warnings = copy(warnings);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record DetectedClassifier(String id, String name, String kind,
            String visibility, double confidence, DetectionRegion region) {
        public DetectedClassifier(String id, String name, String kind,
                String visibility, double confidence) {
            this(id, name, kind, visibility, confidence, null);
        }
    }

    /** Normalized coordinates in the analyzed image, kept only for preview highlighting. */
    public record DetectionRegion(double x, double y, double width, double height) {}

    public record DetectedAttribute(String id, String ownerClassifierId, String name,
            String visibility, DetectedType type, Integer lowerBound, Integer upperBound,
            boolean upperUnlimited, boolean multiplicityKnown, double confidence) {}

    public record DetectedOperation(String id, String ownerClassifierId, String name,
            String visibility, List<DetectedParameter> parameters, DetectedType returnType,
            double confidence) {
        public DetectedOperation {
            parameters = copy(parameters);
        }
    }

    public record DetectedParameter(String id, String name, String direction,
            DetectedType type, int position, Integer lowerBound, Integer upperBound,
            boolean upperUnlimited, boolean multiplicityKnown, double confidence) {}

    /** kind is PRIMITIVE, CLASSIFIER, VOID or UNKNOWN. */
    public record DetectedType(String kind, String value) {}

    public record DetectedRelationship(String id, String type, String name,
            DetectedEnd source, DetectedEnd target, double confidence) {}

    public record DetectedEnd(String classifierId, String roleName, String visibility,
            Integer lowerBound, Integer upperBound, boolean upperUnlimited,
            boolean multiplicityKnown, boolean navigable) {}

    public record DetectionWarning(String code, String message, String elementId) {}
}
