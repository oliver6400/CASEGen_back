package com.casegen.ai.proposal.repository;

import com.casegen.ai.proposal.domain.AiProposalBatch;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AiProposalBatchRepository extends JpaRepository<AiProposalBatch, UUID> {
    Optional<AiProposalBatch> findByProject_IdAndId(UUID projectId, UUID id);
    Optional<AiProposalBatch> findByProject_IdAndClientBatchId(UUID projectId, UUID clientBatchId);
    Page<AiProposalBatch> findByProject_IdOrderByCreatedAtDescIdDesc(UUID projectId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from AiProposalBatch batch where batch.project.id=:projectId and batch.id=:batchId")
    Optional<AiProposalBatch> findForUpdate(UUID projectId, UUID batchId);
}
