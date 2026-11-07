package com.casegen.ai.proposal.dto;

import com.casegen.ai.proposal.domain.AiProposal;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class AiProposalMapper {
    public AiProposalResponse toResponse(AiProposal value) {
        Long currentModel = value.getModel() == null ? null : value.getModel().getRevision();
        Long currentDiagram = value.getDiagram() == null ? null : value.getDiagram().getRevision();
        return new AiProposalResponse(value.getId(), value.getBatch()==null?null:value.getBatch().getId(), value.getProject().getId(),
                value.getSession().getId(), value.getSourceMessage().getId(),
                value.getRequestedBy().getId(), value.getRequestedBy().getDisplayName(),
                value.getStatus(), value.getProposalType(), value.getTitle(), value.getRationale(),
                value.getPayload(), value.getPayloadSchemaVersion(),
                value.getModel() == null ? null : value.getModel().getId(),
                value.getModel() == null ? null : value.getModel().getName(),
                value.getDiagram() == null ? null : value.getDiagram().getId(),
                value.getDiagram() == null ? null : value.getDiagram().getName(),
                value.getBaseModelRevision(), value.getBaseDiagramRevision(), currentModel,
                currentDiagram, value.getModel() != null
                        && !Objects.equals(currentModel, value.getBaseModelRevision()),
                value.getDiagram() != null
                        && !Objects.equals(currentDiagram, value.getBaseDiagramRevision()),
                value.getDecidedBy() == null ? null : value.getDecidedBy().getId(),
                value.getDecidedBy() == null ? null : value.getDecidedBy().getDisplayName(),
                value.getDecidedAt(), value.getDecisionNote(), value.getAppliedModelRevision(),
                value.getAppliedElementId(),
                value.getProvider(), value.getSkill(), value.getCreatedAt(), value.getUpdatedAt());
    }

    public PagedAiProposalResponse toPaged(Page<AiProposal> page) {
        return new PagedAiProposalResponse(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast());
    }
}
