package com.casegen.interoperability.image;

import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

final class DetectedClassDiagramResponseValidator {
    private DetectedClassDiagramResponseValidator() {}

    static DetectedClassDiagram parse(JsonMapper json, String content, String provider) {
        try {
            DetectedClassDiagram result = json.readValue(content, DetectedClassDiagram.class);
            validate(result);
            return result;
        } catch (ImageImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid(provider, exception);
        }
    }

    static void validate(DetectedClassDiagram value) {
        if (value == null || !Double.isFinite(value.confidence())
                || value.confidence() < 0 || value.confidence() > 1) {
            throw new IllegalArgumentException("Invalid confidence");
        }
        required(value.suggestedModelName());
        Set<String> classifierIds = new HashSet<>();
        for (var classifier : value.detectedClassifiers()) {
            required(classifier.id()); required(classifier.name()); required(classifier.kind());
            if (!classifierIds.add(classifier.id())) throw new IllegalArgumentException("Duplicate classifier id");
        }
        Set<String> elementIds = new HashSet<>(classifierIds);
        for (var attribute : value.detectedAttributes()) {
            unique(elementIds, attribute.id());
            reference(classifierIds, attribute.ownerClassifierId());
            required(attribute.name()); type(attribute.type());
        }
        for (var operation : value.detectedOperations()) {
            unique(elementIds, operation.id());
            reference(classifierIds, operation.ownerClassifierId());
            required(operation.name()); type(operation.returnType());
            for (var parameter : operation.parameters()) {
                unique(elementIds, parameter.id()); required(parameter.direction()); type(parameter.type());
            }
        }
        for (var relationship : value.detectedRelationships()) {
            unique(elementIds, relationship.id()); required(relationship.type());
            if (relationship.source() == null || relationship.target() == null)
                throw new IllegalArgumentException("Missing relationship end");
            reference(classifierIds, relationship.source().classifierId());
            reference(classifierIds, relationship.target().classifierId());
        }
    }

    private static void type(DetectedClassDiagram.DetectedType value) {
        if (value == null) throw new IllegalArgumentException("Missing type");
        required(value.kind()); required(value.value());
    }

    private static void unique(Set<String> ids, String id) {
        required(id);
        if (!ids.add(id)) throw new IllegalArgumentException("Duplicate element id");
    }

    private static void reference(Set<String> ids, String id) {
        required(id);
        if (!ids.contains(id)) throw new IllegalArgumentException("Unknown classifier reference");
    }

    private static void required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required value");
    }

    static ImageImportException invalid(String provider, Throwable cause) {
        return new ImageImportException(provider + "_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY,
                "El proveedor devolvió una respuesta de análisis inválida.", cause);
    }
}
