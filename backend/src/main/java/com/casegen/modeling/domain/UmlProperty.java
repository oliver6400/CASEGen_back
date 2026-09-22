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
@Table(name = "uml_properties")
public class UmlProperty {

    @Id
    private UUID elementId;

    @NotNull
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "element_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_properties_element"))
    private UmlElement element;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_classifier_id",
            foreignKey = @ForeignKey(name = "fk_uml_properties_owner"))
    private UmlClassifier ownerClassifier;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PRIVATE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_kind", nullable = false, length = 20)
    private TypeKind typeKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "primitive_type", length = 30)
    private PrimitiveType primitiveType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_classifier_id",
            foreignKey = @ForeignKey(name = "fk_uml_properties_type_classifier"))
    private UmlClassifier typeClassifier;

    @Column(name = "lower_bound", nullable = false)
    private int lowerBound = 1;

    @Column(name = "upper_bound")
    private Integer upperBound;

    @Column(name = "is_static", nullable = false)
    private boolean staticProperty;

    @Column(name = "is_read_only", nullable = false)
    private boolean readOnly;

    @Column(name = "is_derived", nullable = false)
    private boolean derived;

    protected UmlProperty() {
    }

    public UmlProperty(
            UmlElement element,
            UmlClassifier ownerClassifier,
            Visibility visibility,
            TypeKind typeKind,
            PrimitiveType primitiveType,
            UmlClassifier typeClassifier,
            int lowerBound,
            Integer upperBound,
            boolean staticProperty,
            boolean readOnly,
            boolean derived) {
        requireElementType(element, UmlElementType.PROPERTY);
        validateType(typeKind, primitiveType, typeClassifier);
        validateMultiplicity(lowerBound, upperBound);
        this.element = element;
        this.ownerClassifier = java.util.Objects.requireNonNull(ownerClassifier, "Owner classifier is required");
        this.visibility = java.util.Objects.requireNonNull(visibility, "Visibility is required");
        this.typeKind = typeKind;
        this.primitiveType = primitiveType;
        this.typeClassifier = typeClassifier;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.staticProperty = staticProperty;
        this.readOnly = readOnly;
        this.derived = derived;
    }

    public static UmlProperty associationEnd(
            UmlElement element,
            UmlClassifier ownerClassifier,
            Visibility visibility,
            UmlClassifier typeClassifier,
            int lowerBound,
            Integer upperBound) {
        requireElementType(element, UmlElementType.PROPERTY);
        validateMultiplicity(lowerBound, upperBound);
        UmlProperty property = new UmlProperty();
        property.element = element;
        property.ownerClassifier = ownerClassifier;
        property.visibility = java.util.Objects.requireNonNull(visibility, "Visibility is required");
        property.typeKind = TypeKind.CLASSIFIER;
        property.typeClassifier = java.util.Objects.requireNonNull(typeClassifier, "Type classifier is required");
        property.lowerBound = lowerBound;
        property.upperBound = upperBound;
        return property;
    }

    public void update(Visibility visibility, TypeKind typeKind, PrimitiveType primitiveType,
            UmlClassifier typeClassifier, int lowerBound, Integer upperBound,
            boolean staticProperty, boolean readOnly, boolean derived) {
        validateType(typeKind, primitiveType, typeClassifier);
        validateMultiplicity(lowerBound, upperBound);
        this.visibility = java.util.Objects.requireNonNull(visibility, "Visibility is required");
        this.typeKind = typeKind;
        this.primitiveType = primitiveType;
        this.typeClassifier = typeClassifier;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.staticProperty = staticProperty;
        this.readOnly = readOnly;
        this.derived = derived;
    }

    public void updateAssociationEnd(UmlClassifier ownerClassifier, Visibility visibility,
            UmlClassifier typeClassifier, int lowerBound, Integer upperBound) {
        validateMultiplicity(lowerBound, upperBound);
        this.ownerClassifier = ownerClassifier;
        this.visibility = java.util.Objects.requireNonNull(visibility, "Visibility is required");
        this.typeKind = TypeKind.CLASSIFIER;
        this.primitiveType = null;
        this.typeClassifier = java.util.Objects.requireNonNull(typeClassifier, "Type classifier is required");
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.staticProperty = false;
        this.readOnly = false;
        this.derived = false;
    }

    static void requireElementType(UmlElement element, UmlElementType expected) {
        if (element == null || element.getElementType() != expected) {
            throw new IllegalArgumentException("Expected a " + expected + " UML element");
        }
    }

    static void validateType(TypeKind typeKind, PrimitiveType primitiveType, UmlClassifier typeClassifier) {
        if (typeKind == TypeKind.PRIMITIVE && primitiveType != null && typeClassifier == null) {
            return;
        }
        if (typeKind == TypeKind.CLASSIFIER && primitiveType == null && typeClassifier != null) {
            return;
        }
        throw new IllegalArgumentException("Type kind must select exactly one compatible type reference");
    }

    static void validateMultiplicity(int lowerBound, Integer upperBound) {
        if (lowerBound < 0 || (upperBound != null && upperBound < lowerBound)) {
            throw new IllegalArgumentException("Multiplicity bounds are invalid");
        }
    }

    public UUID getElementId() { return elementId; }
    public UmlElement getElement() { return element; }
    public UmlClassifier getOwnerClassifier() { return ownerClassifier; }
    public Visibility getVisibility() { return visibility; }
    public TypeKind getTypeKind() { return typeKind; }
    public PrimitiveType getPrimitiveType() { return primitiveType; }
    public UmlClassifier getTypeClassifier() { return typeClassifier; }
    public int getLowerBound() { return lowerBound; }
    public Integer getUpperBound() { return upperBound; }
    public boolean isStaticProperty() { return staticProperty; }
    public boolean isReadOnly() { return readOnly; }
    public boolean isDerived() { return derived; }
}
