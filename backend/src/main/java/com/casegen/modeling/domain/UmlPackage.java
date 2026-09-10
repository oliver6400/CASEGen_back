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
@Table(name = "uml_packages")
public class UmlPackage {

    @Id
    private UUID elementId;

    @NotNull
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "element_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_uml_packages_element"))
    private UmlElement element;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_package_id",
            foreignKey = @ForeignKey(name = "fk_uml_packages_parent"))
    private UmlPackage parentPackage;

    protected UmlPackage() {
    }

    public UmlPackage(UmlElement element, UmlPackage parentPackage) {
        if (element.getElementType() != UmlElementType.PACKAGE) {
            throw new IllegalArgumentException("UmlPackage requires a PACKAGE element");
        }
        this.element = element;
        this.parentPackage = parentPackage;
    }

    public UUID getElementId() { return elementId; }
    public UmlElement getElement() { return element; }
    public UmlPackage getParentPackage() { return parentPackage; }
}
