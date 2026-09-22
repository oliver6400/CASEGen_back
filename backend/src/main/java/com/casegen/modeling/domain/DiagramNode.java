package com.casegen.modeling.domain;

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
import java.util.UUID;

@Entity
@Table(name = "diagram_nodes", uniqueConstraints = {
        @UniqueConstraint(name = "uq_diagram_nodes_element",
                columnNames = {"diagram_id", "uml_element_id"})
})
public class DiagramNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_diagram_nodes_diagram"))
    private Diagram diagram;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uml_element_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_diagram_nodes_element"))
    private UmlElement umlElement;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(nullable = false)
    private double width;

    @Column(nullable = false)
    private double height;

    @Column(name = "z_index", nullable = false)
    private int zIndex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DiagramNode() {
    }

    public DiagramNode(Diagram diagram, UmlElement umlElement, double x, double y,
            double width, double height, int zIndex) {
        this.diagram = diagram;
        this.umlElement = umlElement;
        updateGeometry(x, y, width, height, zIndex);
    }

    public void updateGeometry(double x, double y, double width, double height, int zIndex) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                || !Double.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Diagram node geometry must be finite and have positive size");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.zIndex = zIndex;
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
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public int getZIndex() { return zIndex; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
