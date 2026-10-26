package com.casegen.ai.session.controller;

import com.casegen.ai.session.dto.AiSessionMapper;
import com.casegen.ai.session.dto.AiSessionResponse;
import com.casegen.ai.session.dto.PagedAiSessionResponse;
import com.casegen.ai.session.service.AiSessionService;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/projects/{projectId}/ai/sessions")
public class AiSessionController {
    private static final int MAX_PAGE_SIZE = 100;

    private final AiSessionService service;
    private final AiSessionMapper mapper;

    public AiSessionController(AiSessionService service, AiSessionMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<AiSessionResponse> startOrReuse(@PathVariable UUID projectId) {
        var result = service.startOrReuse(projectId);
        AiSessionResponse response = mapper.toResponse(result.session());
        if (!result.created()) return ResponseEntity.ok(response);
        URI location = URI.create("/api/projects/" + projectId + "/ai/sessions/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/current")
    public ResponseEntity<AiSessionResponse> current(@PathVariable UUID projectId) {
        return service.getCurrent(projectId)
                .map(session -> ResponseEntity.ok(mapper.toResponse(session)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public PagedAiSessionResponse history(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new ResponseStatusException(BAD_REQUEST, "page must be at least 0");
        if (size < 1) throw new ResponseStatusException(BAD_REQUEST, "size must be at least 1");
        return mapper.toPagedResponse(service.history(projectId,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @GetMapping("/{sessionId}")
    public AiSessionResponse get(@PathVariable UUID projectId, @PathVariable UUID sessionId) {
        return mapper.toResponse(service.getSession(projectId, sessionId));
    }

    @PostMapping("/{sessionId}/join")
    public AiSessionResponse join(@PathVariable UUID projectId, @PathVariable UUID sessionId) {
        return mapper.toResponse(service.join(projectId, sessionId));
    }

    @PostMapping("/{sessionId}/end")
    public AiSessionResponse end(@PathVariable UUID projectId, @PathVariable UUID sessionId) {
        return mapper.toResponse(service.end(projectId, sessionId));
    }
}
