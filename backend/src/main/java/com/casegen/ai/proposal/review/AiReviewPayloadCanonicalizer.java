package com.casegen.ai.proposal.review;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
@Component public class AiReviewPayloadCanonicalizer {private final JsonMapper json;public AiReviewPayloadCanonicalizer(JsonMapper json){this.json=json;}public JsonNode canonicalize(AiReviewPayload payload){if(payload==null)throw new IllegalArgumentException("payload is required");return json.valueToTree(payload);}}
