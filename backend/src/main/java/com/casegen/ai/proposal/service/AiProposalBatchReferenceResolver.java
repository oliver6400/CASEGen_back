package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.domain.*;
import com.casegen.ai.proposal.review.ReviewElementRef;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.repository.UmlElementRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AiProposalBatchReferenceResolver {
    private final UmlElementRepository elements;

    public AiProposalBatchReferenceResolver(UmlElementRepository elements) {
        this.elements = elements;
    }

    public UUID resolve(AiProposal proposal, ReviewElementRef ref,
            List<AiProposalDependency> dependencies, UmlElementType expectedType) {
        if (ref == null) throw invalid("Element reference is required");
        UUID id;
        if (ref.elementId() != null) {
            id = ref.elementId();
        } else {
            AiProposal referenced = dependencies.stream().map(AiProposalDependency::getDependsOn)
                    .filter(value -> ref.proposalRef().equals(value.getClientRef()))
                    .findFirst().orElseThrow(() -> invalid(
                            "proposalRef must identify a declared dependency in the same batch"));
            if (referenced.getStatus() != AiProposalStatus.APPLIED
                    || referenced.getAppliedElementId() == null) {
                throw new AiProposalException(
                        AiProposalErrorCode.PROPOSAL_DEPENDENCY_NOT_APPLIED,
                        "Referenced proposal dependency has not been applied");
            }
            id = referenced.getAppliedElementId();
        }
        UmlElement element = elements.findById(id)
                .orElseThrow(() -> invalid("Referenced UML element no longer exists"));
        if (!element.getModel().getId().equals(proposal.getModel().getId())
                || element.getElementType() != expectedType) {
            throw invalid("Referenced UML element has an incompatible model or type");
        }
        return id;
    }

    private AiProposalException invalid(String message) {
        return new AiProposalException(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID, message);
    }
}
