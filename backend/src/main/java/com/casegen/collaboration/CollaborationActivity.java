package com.casegen.collaboration;

import com.casegen.auth.domain.User;
import com.casegen.modeling.domain.Diagram;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.project.domain.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collaboration_activity")
public class CollaborationActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private UmlModel model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagram_id")
    private Diagram diagram;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private RealtimeEventType eventType;

    @Column(name = "element_id")
    private UUID elementId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "model_revision")
    private Long modelRevision;

    @Column(name = "diagram_revision")
    private Long diagramRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CollaborationActivity() {
    }

    public CollaborationActivity(Project project, UmlModel model, Diagram diagram, User actor,
            RealtimeEventType eventType, UUID elementId, String description,
            Long modelRevision, Long diagramRevision, Instant createdAt) {
        this.project = project;
        this.model = model;
        this.diagram = diagram;
        this.actor = actor;
        this.eventType = eventType;
        this.elementId = elementId;
        this.description = description;
        this.modelRevision = modelRevision;
        this.diagramRevision = diagramRevision;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public UmlModel getModel() { return model; }
    public Diagram getDiagram() { return diagram; }
    public User getActor() { return actor; }
    public RealtimeEventType getEventType() { return eventType; }
    public UUID getElementId() { return elementId; }
    public String getDescription() { return description; }
    public Long getModelRevision() { return modelRevision; }
    public Long getDiagramRevision() { return diagramRevision; }
    public Instant getCreatedAt() { return createdAt; }
}
