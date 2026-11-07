package com.casegen.ai.proposal.repository;

import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.domain.AiProposalStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface AiProposalRepository extends JpaRepository<AiProposal, UUID> {

    @Query("select proposal.batch.id from AiProposal proposal where proposal.id=:proposalId and proposal.project.id=:projectId")
    Optional<UUID> findBatchId(UUID projectId, UUID proposalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select proposal from AiProposal proposal where proposal.batch.id=:batchId order by proposal.batchOrder, proposal.id")
    java.util.List<AiProposal> findBatchChildrenForUpdate(UUID batchId);

    java.util.List<AiProposal> findByBatch_IdOrderByBatchOrderAsc(UUID batchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select proposal from AiProposal proposal
            where proposal.id = :proposalId and proposal.project.id = :projectId
            """)
    Optional<AiProposal> findForDecision(UUID projectId, UUID proposalId);

    @EntityGraph(attributePaths = {"project", "session", "requestedBy", "model", "diagram",
            "sourceMessage"})
    Optional<AiProposal> findByProject_IdAndClientProposalId(UUID projectId, UUID clientProposalId);

    boolean existsByProject_IdAndClientProposalId(UUID projectId, UUID clientProposalId);

    @EntityGraph(attributePaths = {"project", "session", "requestedBy", "model", "diagram", "sourceMessage"})
    Optional<AiProposal> findByIdAndProject_Id(UUID id, UUID projectId);

    @EntityGraph(attributePaths = {"project", "session", "requestedBy", "model", "diagram", "sourceMessage"})
    Page<AiProposal> findByProject_IdOrderByCreatedAtDescIdDesc(UUID projectId, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "session", "requestedBy", "model", "diagram", "sourceMessage"})
    Page<AiProposal> findByProject_IdAndStatusOrderByCreatedAtDescIdDesc(
            UUID projectId, AiProposalStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "session", "requestedBy", "model", "diagram", "sourceMessage"})
    Page<AiProposal> findByProject_IdAndModel_IdOrderByCreatedAtDescIdDesc(
            UUID projectId, UUID modelId, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "session", "requestedBy", "model", "diagram", "sourceMessage"})
    Page<AiProposal> findByProject_IdAndDiagram_IdOrderByCreatedAtDescIdDesc(
            UUID projectId, UUID diagramId, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "session", "requestedBy", "model", "diagram", "sourceMessage"})
    @Query("""
            select proposal from AiProposal proposal
            where proposal.project.id = :projectId
              and proposal.batch is null
              and (:status is null or proposal.status = :status)
              and (:sessionId is null or proposal.session.id = :sessionId)
              and (:modelId is null or proposal.model.id = :modelId)
              and (:diagramId is null or proposal.diagram.id = :diagramId)
              and (:proposalType is null or proposal.proposalType = :proposalType)
            order by proposal.createdAt desc, proposal.id desc
            """)
    Page<AiProposal> findFiltered(UUID projectId, AiProposalStatus status, UUID sessionId,
            UUID modelId, UUID diagramId,
            com.casegen.ai.proposal.domain.AiProposalType proposalType, Pageable pageable);
}
