package com.casegen.ai.proposal.domain;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "ai_proposal_dependencies")
@IdClass(AiProposalDependencyId.class)
public class AiProposalDependency {
    @Id @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false) private AiProposal proposal;
    @Id @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depends_on_proposal_id", nullable = false) private AiProposal dependsOn;
    protected AiProposalDependency() { }
    public static AiProposalDependency create(AiProposal proposal, AiProposal dependsOn) {
        Objects.requireNonNull(proposal, "proposal is required");
        Objects.requireNonNull(dependsOn, "dependsOn is required");
        if (proposal == dependsOn || proposal.getId() != null && proposal.getId().equals(dependsOn.getId())) {
            throw new IllegalArgumentException("proposal cannot depend on itself");
        }
        if (proposal.getBatch() == null || dependsOn.getBatch() == null) {
            throw new IllegalArgumentException("dependencies require batched proposals");
        }
        if (proposal.getBatch() != dependsOn.getBatch()
                && (proposal.getBatch().getId() == null || !proposal.getBatch().getId().equals(dependsOn.getBatch().getId()))) {
            throw new IllegalArgumentException("dependencies must belong to the same batch");
        }
        AiProposalDependency value = new AiProposalDependency();
        value.proposal = proposal;
        value.dependsOn = dependsOn;
        return value;
    }
    public AiProposal getProposal() { return proposal; }
    public AiProposal getDependsOn() { return dependsOn; }
}
