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
import java.util.UUID;

@Entity
@Table(name = "uml_classifiers")
public class UmlClassifier {

    @Id
    private UUID elementId;

    @NotNull
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "element_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_classifiers_element"))
    private UmlElement element;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id",
            foreignKey = @ForeignKey(name = "fk_uml_classifiers_package"))
    private UmlPackage umlPackage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClassifierKind kind;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PUBLIC;

    @Column(name = "is_abstract", nullable = false)
    private boolean abstractClassifier;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected UmlClassifier() {
    }

    public UmlClassifier(UmlElement element, UmlPackage umlPackage, ClassifierKind kind,
            Visibility visibility, boolean abstractClassifier, boolean active) {
        if (element.getElementType() != UmlElementType.CLASSIFIER) {
            throw new IllegalArgumentException("UmlClassifier requires a CLASSIFIER element");
        }
        if (kind == ClassifierKind.INTERFACE && active) {
            throw new IllegalArgumentException("An INTERFACE cannot be active");
        }
        this.element = element;
        this.umlPackage = umlPackage;
        this.kind = kind;
        this.visibility = visibility;
        this.abstractClassifier = abstractClassifier;
        this.active = active;
    }

    public void update(UmlPackage umlPackage, ClassifierKind kind, Visibility visibility,
            boolean abstractClassifier, boolean active) {
        if (kind == ClassifierKind.INTERFACE && active) {
            throw new IllegalArgumentException("An INTERFACE cannot be active");
        }
        this.umlPackage = umlPackage;
        this.kind = java.util.Objects.requireNonNull(kind, "Classifier kind is required");
        this.visibility = java.util.Objects.requireNonNull(visibility, "Visibility is required");
        this.abstractClassifier = abstractClassifier;
        this.active = active;
    }

    public UUID getElementId() { return elementId; }
    public UmlElement getElement() { return element; }
    public UmlPackage getUmlPackage() { return umlPackage; }
    public ClassifierKind getKind() { return kind; }
    public Visibility getVisibility() { return visibility; }
    public boolean isAbstractClassifier() { return abstractClassifier; }
    public boolean isActive() { return active; }
}
