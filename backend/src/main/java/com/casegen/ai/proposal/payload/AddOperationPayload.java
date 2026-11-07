package com.casegen.ai.proposal.payload;

import com.casegen.modeling.domain.Visibility;
import java.util.UUID;

public record AddOperationPayload(UUID ownerClassifierId, String name, Visibility visibility,
        Boolean abstractOperation, Boolean staticOperation, Boolean query)
        implements AiProposalPayload {
}
