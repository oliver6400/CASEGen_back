package com.casegen.modeling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "uml_generalizations")
public class UmlGeneralization {

    @Id
    private UUID elementId;

    @NotNull
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "element_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_generalizations_element"))
    private UmlElement element;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specific_classifier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_generalizations_specific"))
    private UmlClassifier specificClassifier;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "general_classifier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_generalizations_general"))
    private UmlClassifier generalClassifier;

    @Column(name = "is_substitutable", nullable = false)
    private boolean substitutable = true;

    protected UmlGeneralization() {
    }

    public UmlGeneralization(UmlElement element, UmlClassifier specificClassifier,
            UmlClassifier generalClassifier, boolean substitutable) {
        UmlProperty.requireElementType(element, UmlElementType.GENERALIZATION);
        if (specificClassifier.getElementId().equals(generalClassifier.getElementId())) {
            throw new IllegalArgumentException("Generalization classifiers must be distinct");
        }
        this.element = element;
        this.specificClassifier = specificClassifier;
        this.generalClassifier = generalClassifier;
        this.substitutable = substitutable;
    }

    public UUID getElementId() { return elementId; }
    public UmlElement getElement() { return element; }
    public UmlClassifier getSpecificClassifier() { return specificClassifier; }
    public UmlClassifier getGeneralClassifier() { return generalClassifier; }
    public boolean isSubstitutable() { return substitutable; }
}
