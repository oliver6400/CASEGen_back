package com.casegen.ai.proposal.review;

import java.util.UUID;

public record ReviewElementRef(UUID elementId, String proposalRef) {
    public ReviewElementRef {
        proposalRef = proposalRef == null ? null : proposalRef.trim();
        if ((elementId == null) == (proposalRef == null || proposalRef.isBlank())) {
            throw new IllegalArgumentException("exactly one of elementId or proposalRef is required");
        }
        if (proposalRef != null && (!proposalRef.matches("[a-z0-9][a-z0-9-]*") || proposalRef.length() > 80)) {
            throw new IllegalArgumentException("proposalRef has an invalid format");
        }
    }
}
