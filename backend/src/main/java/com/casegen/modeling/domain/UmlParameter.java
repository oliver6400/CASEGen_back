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
@Table(name = "uml_parameters")
public class UmlParameter {

    @Id
    private UUID elementId;

    @NotNull
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "element_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_parameters_element"))
    private UmlElement element;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_parameters_operation"))
    private UmlOperation operation;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParameterDirection direction = ParameterDirection.IN;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type_kind", nullable = false, length = 20)
    private TypeKind typeKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "primitive_type", length = 30)
    private PrimitiveType primitiveType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_classifier_id",
            foreignKey = @ForeignKey(name = "fk_uml_parameters_type_classifier"))
    private UmlClassifier typeClassifier;

    @Column(name = "lower_bound", nullable = false)
    private int lowerBound = 1;

    @Column(name = "upper_bound")
    private Integer upperBound;

    @Column(nullable = false)
    private int position;

    protected UmlParameter() {
    }

    public UmlParameter(
            UmlElement element,
            UmlOperation operation,
            ParameterDirection direction,
            TypeKind typeKind,
            PrimitiveType primitiveType,
            UmlClassifier typeClassifier,
            int lowerBound,
            Integer upperBound,
            int position) {
        UmlProperty.requireElementType(element, UmlElementType.PARAMETER);
        UmlProperty.validateType(typeKind, primitiveType, typeClassifier);
        UmlProperty.validateMultiplicity(lowerBound, upperBound);
        if (position < 0) {
            throw new IllegalArgumentException("Parameter position must not be negative");
        }
        this.element = element;
        this.operation = Objects.requireNonNull(operation, "Operation is required");
        this.direction = Objects.requireNonNull(direction, "Direction is required");
        this.typeKind = typeKind;
        this.primitiveType = primitiveType;
        this.typeClassifier = typeClassifier;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.position = position;
    }

    public void update(ParameterDirection direction, TypeKind typeKind, PrimitiveType primitiveType,
            UmlClassifier typeClassifier, int lowerBound, Integer upperBound, int position) {
        UmlProperty.validateType(typeKind, primitiveType, typeClassifier);
        UmlProperty.validateMultiplicity(lowerBound, upperBound);
        if (position < 0) {
            throw new IllegalArgumentException("Parameter position must not be negative");
        }
        this.direction = Objects.requireNonNull(direction, "Direction is required");
        this.typeKind = typeKind;
        this.primitiveType = primitiveType;
        this.typeClassifier = typeClassifier;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.position = position;
    }

    public UUID getElementId() { return elementId; }
    public UmlElement getElement() { return element; }
    public UmlOperation getOperation() { return operation; }
    public ParameterDirection getDirection() { return direction; }
    public TypeKind getTypeKind() { return typeKind; }
    public PrimitiveType getPrimitiveType() { return primitiveType; }
    public UmlClassifier getTypeClassifier() { return typeClassifier; }
    public int getLowerBound() { return lowerBound; }
    public Integer getUpperBound() { return upperBound; }
    public int getPosition() { return position; }
}
