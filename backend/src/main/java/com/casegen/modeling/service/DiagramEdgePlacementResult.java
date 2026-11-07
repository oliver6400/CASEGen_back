package com.casegen.modeling.service;

import java.util.UUID;

public record DiagramEdgePlacementResult(Status status, UUID edgeId) {
    public enum Status {
        CREATED,
        ALREADY_EXISTS,
        ENDPOINT_NOT_VISIBLE
    }
}
