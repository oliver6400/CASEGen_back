package com.casegen.ai.proposal.realtime;

import com.casegen.ai.proposal.dto.AiProposalMapper;
import com.casegen.ai.proposal.dto.AiProposalResponse;
import com.casegen.ai.proposal.repository.AiProposalRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProposalQueryService {
    private final AiProposalRepository proposals;
    private final AiProposalMapper mapper;

    public AiProposalQueryService(AiProposalRepository proposals, AiProposalMapper mapper) {
        this.proposals = proposals;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public AiProposalResponse loadResponse(UUID projectId, UUID proposalId) {
        return proposals.findByIdAndProject_Id(proposalId, projectId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new IllegalStateException(
                        "Committed AI proposal could not be reloaded"));
    }
}
