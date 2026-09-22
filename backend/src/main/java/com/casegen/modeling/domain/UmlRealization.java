package com.casegen.modeling.domain;

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
@Table(name = "uml_realizations")
public class UmlRealization {

    @Id
    private UUID elementId;

    @NotNull
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "element_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_realizations_element"))
    private UmlElement element;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_classifier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_realizations_client"))
    private UmlClassifier clientClassifier;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_classifier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_realizations_supplier"))
    private UmlClassifier supplierClassifier;

    protected UmlRealization() {
    }

    public UmlRealization(UmlElement element, UmlClassifier clientClassifier,
            UmlClassifier supplierClassifier) {
        UmlProperty.requireElementType(element, UmlElementType.REALIZATION);
        if (clientClassifier.getElementId().equals(supplierClassifier.getElementId())) {
            throw new IllegalArgumentException("Realization classifiers must be distinct");
        }
        this.element = element;
        this.clientClassifier = clientClassifier;
        this.supplierClassifier = supplierClassifier;
    }

    public UUID getElementId() { return elementId; }
    public UmlElement getElement() { return element; }
    public UmlClassifier getClientClassifier() { return clientClassifier; }
    public UmlClassifier getSupplierClassifier() { return supplierClassifier; }
}
