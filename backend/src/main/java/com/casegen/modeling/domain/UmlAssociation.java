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
@Table(name = "uml_associations")
public class UmlAssociation {

    @Id
    private UUID elementId;

    @NotNull
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "element_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_associations_element"))
    private UmlElement element;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id",
            foreignKey = @ForeignKey(name = "fk_uml_associations_package"))
    private UmlPackage umlPackage;

    protected UmlAssociation() {
    }

    public UmlAssociation(UmlElement element, UmlPackage umlPackage) {
        UmlProperty.requireElementType(element, UmlElementType.ASSOCIATION);
        this.element = element;
        this.umlPackage = umlPackage;
    }

    public void update(UmlPackage umlPackage) {
        this.umlPackage = umlPackage;
    }

    public UUID getElementId() { return elementId; }
    public UmlElement getElement() { return element; }
    public UmlPackage getUmlPackage() { return umlPackage; }
}
