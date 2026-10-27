package com.casegen.ai.message.controller;

import com.casegen.ai.message.dto.AiMessageMapper;
import com.casegen.ai.message.dto.PagedAiMessageResponse;
import com.casegen.ai.message.dto.SendAiMessageRequest;
import com.casegen.ai.message.dto.SendAiMessageResponse;
import com.casegen.ai.message.service.AiMessageService;
import com.casegen.ai.message.service.SendAiMessageResult;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/ai/sessions/{sessionId}/messages")
public class AiMessageController {
    private final AiMessageService service;
    private final AiMessageMapper mapper;

    public AiMessageController(AiMessageService service, AiMessageMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public PagedAiMessageResponse history(@PathVariable UUID projectId,
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return mapper.toPagedResponse(service.history(projectId, sessionId, page, size));
    }

    @PostMapping
    public ResponseEntity<SendAiMessageResponse> send(@PathVariable UUID projectId,
            @PathVariable UUID sessionId, @Valid @RequestBody SendAiMessageRequest request) {
        SendAiMessageResult result = service.send(projectId, sessionId, request);
        SendAiMessageResponse response = new SendAiMessageResponse(
                mapper.toResponse(result.userMessage()),
                mapper.toResponse(result.assistantMessage()), result.replayed());
        if (result.replayed()) return ResponseEntity.ok(response);
        URI location = URI.create("/api/projects/" + projectId + "/ai/sessions/" + sessionId
                + "/messages/" + response.userMessage().id());
        return ResponseEntity.created(location).body(response);
    }
}
