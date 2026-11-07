package com.casegen.ai.proposal.repository;

import com.casegen.ai.proposal.domain.AiProposalDependency;
import com.casegen.ai.proposal.domain.AiProposalDependencyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiProposalDependencyRepository extends JpaRepository<AiProposalDependency, AiProposalDependencyId> {
    List<AiProposalDependency> findByProposal_Id(UUID proposalId);
    @Query("select dependency from AiProposalDependency dependency where dependency.proposal.batch.id=:batchId")
    List<AiProposalDependency> findByBatchId(UUID batchId);
}
