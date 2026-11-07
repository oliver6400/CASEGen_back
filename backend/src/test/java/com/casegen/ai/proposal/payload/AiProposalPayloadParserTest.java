package com.casegen.ai.proposal.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.validation.AiProposalValidationErrorCode;
import com.casegen.ai.proposal.validation.AiProposalValidationException;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AiProposalPayloadParserTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final AiProposalPayloadParser parser = new AiProposalPayloadParser(json);

    @ParameterizedTest
    @MethodSource("validPayloads")
    void parsesEachOfficialPayload(AiProposalType type, Map<String, Object> source,
            Class<? extends AiProposalPayload> expected) {
        assertThat(parser.parse(type, json.valueToTree(source))).isInstanceOf(expected);
    }

    @Test
    void rejectsUnknownField() {
        JsonNode payload = json.valueToTree(Map.of("kind", "CLASS", "name", "Customer",
                "abstractClassifier", false, "active", true, "foo", "bar"));
        assertThatThrownBy(() -> parser.parse(AiProposalType.ADD_CLASSIFIER, payload))
                .isInstanceOf(AiProposalValidationException.class)
                .extracting(error -> ((AiProposalValidationException) error).getCode())
                .isEqualTo(AiProposalValidationErrorCode.UNKNOWN_PAYLOAD_FIELD);
    }

    @Test
    void rejectsInvalidEnum() {
        JsonNode payload = json.valueToTree(Map.of("kind", "TABLE", "name", "Customer", "visibility", "PUBLIC",
                "abstractClassifier", false, "active", true));
        rejectsInvalid(() -> parser.parse(AiProposalType.ADD_CLASSIFIER, payload));
    }

    @Test
    void rejectsMissingRequiredField() {
        JsonNode payload = json.valueToTree(Map.of("name", "save", "visibility", "PUBLIC",
                "abstractOperation", false, "staticOperation", false, "query", false));
        rejectsInvalid(() -> parser.parse(AiProposalType.ADD_OPERATION, payload));
    }

    @Test
    void rejectsArrayAndPrimitive() {
        rejectsInvalid(() -> parser.parse(AiProposalType.ADD_CLASSIFIER,
                json.valueToTree(java.util.List.of("unsafe"))));
        rejectsInvalid(() -> parser.parse(AiProposalType.ADD_CLASSIFIER, json.valueToTree("unsafe")));
    }

    @Test
    void rejectsTypePayloadMismatch() {
        AiProposalPayload payload = parser.parse(AiProposalType.ADD_OPERATION,
                json.valueToTree(operation()));
        rejectsInvalid(() -> parser.canonicalize(AiProposalType.ADD_CLASSIFIER, payload));
    }

    @Test
    void roundTripProducesCanonicalObject() {
        JsonNode original = json.valueToTree(Map.of("name", "Customer", "kind", "CLASS", "visibility", "PUBLIC",
                "active", true, "abstractClassifier", false));
        JsonNode canonical = parser.canonicalize(AiProposalType.ADD_CLASSIFIER,
                parser.parse(AiProposalType.ADD_CLASSIFIER, original));
        assertThat(canonical.isObject()).isTrue();
        assertThat(canonical.path("kind").asText()).isEqualTo("CLASS");
        assertThat(canonical.path("name").asText()).isEqualTo("Customer");
        assertThat(canonical.has("foo")).isFalse();
    }

    private void rejectsInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(AiProposalValidationException.class)
                .extracting(error -> ((AiProposalValidationException) error).getCode())
                .isEqualTo(AiProposalValidationErrorCode.INVALID_PAYLOAD);
    }

    static Stream<Arguments> validPayloads() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        return Stream.of(
                Arguments.of(AiProposalType.ADD_CLASSIFIER,
                        Map.of("kind", "CLASS", "name", "Customer", "abstractClassifier", false,
                                "active", true, "visibility", "PUBLIC"), AddClassifierPayload.class),
                Arguments.of(AiProposalType.ADD_ATTRIBUTE,
                        Map.of("ownerClassifierId", first, "name", "code", "visibility", "PRIVATE", "typeKind", "PRIMITIVE",
                                "primitiveType", "STRING", "lowerBound", 1, "staticProperty", false,
                                "readOnly", false, "derived", false), AddAttributePayload.class),
                Arguments.of(AiProposalType.ADD_OPERATION, operation(), AddOperationPayload.class),
                Arguments.of(AiProposalType.ADD_ASSOCIATION,
                        Map.of("firstEnd", end(first), "secondEnd", end(second)),
                        AddAssociationPayload.class),
                Arguments.of(AiProposalType.ADD_GENERALIZATION,
                        Map.of("specificClassifierId", first, "generalClassifierId", second,
                                "substitutable", true), AddGeneralizationPayload.class),
                Arguments.of(AiProposalType.ADD_REALIZATION,
                        Map.of("clientClassifierId", first, "supplierClassifierId", second),
                        AddRealizationPayload.class));
    }

    private static Map<String, Object> operation() {
        return Map.of("ownerClassifierId", UUID.randomUUID(), "name", "save",
                "visibility", "PUBLIC", "abstractOperation", false, "staticOperation", false, "query", false);
    }

    private static Map<String, Object> end(UUID classifierId) {
        return Map.of("classifierId", classifierId, "lowerBound", 1, "upperBound", 1,
                "navigable", true, "aggregation", "NONE", "ownership", "ASSOCIATION");
    }
}
