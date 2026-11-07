package com.casegen.ai.proposal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateAiProposalRequest(@NotNull UUID clientProposalId) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unknown create proposal field: " + field);
    }
}
