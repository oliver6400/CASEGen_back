package com.casegen.modeling.domain;

import com.casegen.modeling.dto.DiagramWaypoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "diagram_edges", uniqueConstraints = {
        @UniqueConstraint(name = "uq_diagram_edges_element",
                columnNames = {"diagram_id", "uml_element_id"})
})
public class DiagramEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_diagram_edges_diagram"))
    private Diagram diagram;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uml_element_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_diagram_edges_element"))
    private UmlElement umlElement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_node_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_diagram_edges_source"))
    private DiagramNode sourceNode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_node_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_diagram_edges_target"))
    private DiagramNode targetNode;

    @Column(name = "source_side", nullable = false, length = 10)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private DiagramSide sourceSide;

    @Column(name = "target_side", nullable = false, length = 10)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private DiagramSide targetSide;

    @Column(name = "source_anchor_index", nullable = false)
    private int sourceAnchorIndex;

    @Column(name = "target_anchor_index", nullable = false)
    private int targetAnchorIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "waypoints_json", nullable = false, columnDefinition = "jsonb")
    private List<DiagramWaypoint> waypoints = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DiagramEdge() {
    }

    public DiagramEdge(Diagram diagram, UmlElement umlElement,
            DiagramNode sourceNode, DiagramNode targetNode,
            DiagramSide sourceSide, DiagramSide targetSide,
            int sourceAnchorIndex, int targetAnchorIndex,
            List<DiagramWaypoint> waypoints) {
        this.diagram = diagram;
        this.umlElement = umlElement;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        updateLayout(sourceSide, targetSide, sourceAnchorIndex, targetAnchorIndex, waypoints);
    }

    public void updateLayout(
            DiagramSide sourceSide, DiagramSide targetSide,
            int sourceAnchorIndex, int targetAnchorIndex,
            List<DiagramWaypoint> waypoints) {
        if (sourceSide == null || targetSide == null
                || sourceAnchorIndex < 0 || targetAnchorIndex < 0) {
            throw new IllegalArgumentException("Diagram edge anchors are invalid");
        }
        this.sourceSide = sourceSide;
        this.targetSide = targetSide;
        this.sourceAnchorIndex = sourceAnchorIndex;
        this.targetAnchorIndex = targetAnchorIndex;
        this.waypoints = waypoints == null ? new ArrayList<>() : new ArrayList<>(waypoints);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Diagram getDiagram() { return diagram; }
    public UmlElement getUmlElement() { return umlElement; }
    public DiagramNode getSourceNode() { return sourceNode; }
    public DiagramNode getTargetNode() { return targetNode; }
    public DiagramSide getSourceSide() { return sourceSide; }
    public DiagramSide getTargetSide() { return targetSide; }
    public int getSourceAnchorIndex() { return sourceAnchorIndex; }
    public int getTargetAnchorIndex() { return targetAnchorIndex; }
    public List<DiagramWaypoint> getWaypoints() { return List.copyOf(waypoints); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
