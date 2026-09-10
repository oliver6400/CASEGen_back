package com.casegen.modeling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "uml_elements")
public class UmlElement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_elements_model"))
    private UmlModel model;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", nullable = false, length = 40)
    private UmlElementType elementType;

    @Size(max = 180)
    @Column(length = 180)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String documentation;

    @Size(max = 255)
    @Column(name = "external_xmi_id", length = 255)
    private String externalXmiId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UmlElement() {
    }

    public UmlElement(UmlModel model, UmlElementType elementType, String name, String documentation) {
        this.model = model;
        this.elementType = elementType;
        this.name = name;
        this.documentation = documentation;
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
    public UmlModel getModel() { return model; }
    public UmlElementType getElementType() { return elementType; }
    public String getName() { return name; }
    public String getDocumentation() { return documentation; }
    public String getExternalXmiId() { return externalXmiId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
