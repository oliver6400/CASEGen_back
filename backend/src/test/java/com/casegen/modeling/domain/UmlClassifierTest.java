package com.casegen.modeling.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.casegen.project.domain.Project;
import org.junit.jupiter.api.Test;

class UmlClassifierTest {

    @Test
    void rejectsActiveInterface() {
        UmlModel model = new UmlModel(new Project("Project", null), "Domain", null, null);
        UmlElement element = new UmlElement(model, UmlElementType.CLASSIFIER, "Identifiable", null);

        assertThrows(IllegalArgumentException.class,
                () -> new UmlClassifier(
                        element, null, ClassifierKind.INTERFACE, Visibility.PUBLIC, false, true));
    }

    @Test
    void rejectsElementWithWrongType() {
        UmlModel model = new UmlModel(new Project("Project", null), "Domain", null, null);
        UmlElement element = new UmlElement(model, UmlElementType.PACKAGE, "Customer", null);

        assertThrows(IllegalArgumentException.class,
                () -> new UmlClassifier(
                        element, null, ClassifierKind.CLASS, Visibility.PUBLIC, false, false));
    }
}
