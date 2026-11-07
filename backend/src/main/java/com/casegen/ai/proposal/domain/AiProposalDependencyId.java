package com.casegen.ai.proposal.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AiProposalDependencyId implements Serializable {
    private UUID proposal;
    private UUID dependsOn;
    public AiProposalDependencyId() { }
    public AiProposalDependencyId(UUID proposal, UUID dependsOn) { this.proposal = proposal; this.dependsOn = dependsOn; }
    @Override public boolean equals(Object other) { return other instanceof AiProposalDependencyId value
            && Objects.equals(proposal, value.proposal) && Objects.equals(dependsOn, value.dependsOn); }
    @Override public int hashCode() { return Objects.hash(proposal, dependsOn); }
}
