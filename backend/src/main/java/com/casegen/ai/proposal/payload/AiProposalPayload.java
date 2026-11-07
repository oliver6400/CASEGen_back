package com.casegen.ai.proposal.payload;

public sealed interface AiProposalPayload permits AddClassifierPayload, AddAttributePayload,
        AddOperationPayload, AddAssociationPayload, AddGeneralizationPayload, AddRealizationPayload {
}
