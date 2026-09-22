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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Entity
@Table(name = "uml_association_ends")
public class UmlAssociationEnd {

    @Id
    private UUID propertyId;

    @NotNull
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_association_ends_property"))
    private UmlProperty property;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_association_ends_association"))
    private UmlAssociation association;

    @Column(nullable = false)
    private boolean navigable;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AggregationKind aggregation = AggregationKind.NONE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssociationEndOwnership ownership = AssociationEndOwnership.ASSOCIATION;

    @Min(1)
    @Max(2)
    @Column(name = "member_order", nullable = false)
    private short memberOrder;

    protected UmlAssociationEnd() {
    }

    public UmlAssociationEnd(UmlProperty property, UmlAssociation association, boolean navigable,
            AggregationKind aggregation, AssociationEndOwnership ownership, short memberOrder) {
        if (memberOrder < 1 || memberOrder > 2) {
            throw new IllegalArgumentException("Association end order must be 1 or 2");
        }
        this.property = java.util.Objects.requireNonNull(property, "Property is required");
        if (property.getTypeKind() != TypeKind.CLASSIFIER) {
            throw new IllegalArgumentException("An association end must be typed by a Classifier");
        }
        if ((ownership == AssociationEndOwnership.ASSOCIATION && property.getOwnerClassifier() != null)
                || (ownership == AssociationEndOwnership.CLASSIFIER && property.getOwnerClassifier() == null)) {
            throw new IllegalArgumentException("Association end ownership and property owner do not match");
        }
        this.association = java.util.Objects.requireNonNull(association, "Association is required");
        this.navigable = navigable;
        this.aggregation = java.util.Objects.requireNonNull(aggregation, "Aggregation is required");
        this.ownership = java.util.Objects.requireNonNull(ownership, "Ownership is required");
        this.memberOrder = memberOrder;
    }

    public void update(boolean navigable, AggregationKind aggregation,
            AssociationEndOwnership ownership) {
        this.navigable = navigable;
        this.aggregation = java.util.Objects.requireNonNull(aggregation, "Aggregation is required");
        this.ownership = java.util.Objects.requireNonNull(ownership, "Ownership is required");
    }

    public UUID getPropertyId() { return propertyId; }
    public UmlProperty getProperty() { return property; }
    public UmlAssociation getAssociation() { return association; }
    public boolean isNavigable() { return navigable; }
    public AggregationKind getAggregation() { return aggregation; }
    public AssociationEndOwnership getOwnership() { return ownership; }
    public short getMemberOrder() { return memberOrder; }
}
