package com.casegen.collaboration;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagrams/{diagramId}/collaboration")
public class CollaborationActivityController {
    private final CollaborationActivityService activities;

    public CollaborationActivityController(CollaborationActivityService activities) {
        this.activities = activities;
    }

    @GetMapping("/activity")
    public List<CollaborationActivityResponse> list(@PathVariable UUID diagramId) {
        return activities.listForDiagram(diagramId);
    }
}
