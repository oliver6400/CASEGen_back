package com.casegen.ai.proposal.payload;

import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.Visibility;
import java.util.UUID;

public record AddClassifierPayload(ClassifierKind kind, String name, UUID packageId,
        Visibility visibility, Boolean abstractClassifier, Boolean active,
        String documentation) implements AiProposalPayload {
}
