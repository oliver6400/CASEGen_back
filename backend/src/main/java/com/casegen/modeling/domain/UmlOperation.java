package com.casegen.modeling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "uml_operations")
public class UmlOperation {

    @Id
    private UUID elementId;

    @NotNull
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "element_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_operations_element"))
    private UmlElement element;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_classifier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_operations_owner"))
    private UmlClassifier ownerClassifier;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PUBLIC;

    @Column(name = "is_abstract", nullable = false)
    private boolean abstractOperation;

    @Column(name = "is_static", nullable = false)
    private boolean staticOperation;

    @Column(name = "is_query", nullable = false)
    private boolean query;

    protected UmlOperation() {
    }

    public UmlOperation(
            UmlElement element,
            UmlClassifier ownerClassifier,
            Visibility visibility,
            boolean abstractOperation,
            boolean staticOperation,
            boolean query) {
        UmlProperty.requireElementType(element, UmlElementType.OPERATION);
        this.element = element;
        this.ownerClassifier = Objects.requireNonNull(ownerClassifier, "Owner classifier is required");
        this.visibility = Objects.requireNonNull(visibility, "Visibility is required");
        this.abstractOperation = abstractOperation;
        this.staticOperation = staticOperation;
        this.query = query;
    }

    public void update(Visibility visibility, boolean abstractOperation,
            boolean staticOperation, boolean query) {
        this.visibility = Objects.requireNonNull(visibility, "Visibility is required");
        this.abstractOperation = abstractOperation;
        this.staticOperation = staticOperation;
        this.query = query;
    }

    public UUID getElementId() { return elementId; }
    public UmlElement getElement() { return element; }
    public UmlClassifier getOwnerClassifier() { return ownerClassifier; }
    public Visibility getVisibility() { return visibility; }
    public boolean isAbstractOperation() { return abstractOperation; }
    public boolean isStaticOperation() { return staticOperation; }
    public boolean isQuery() { return query; }
}
