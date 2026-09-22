package com.casegen.interoperability.xmi;

import static com.casegen.interoperability.xmi.XmiImportData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ImportedClassDiagramCreatorTest {
    private final ImportedClassDiagramCreator creator =
            new ImportedClassDiagramCreator(mock(JdbcTemplate.class));

    @Test
    void createsDeterministicNonOverlappingGridForFiftyClassifiers() {
        List<ClassifierData> classifiers = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            classifiers.add(classifier("c" + index, "Classifier" + index, "CLASS"));
        }
        XmiImportData data = data(classifiers, List.of(), List.of(), List.of());

        var first = creator.layout(data);
        var second = creator.layout(data);

        assertEquals(first, second);
        assertEquals(50, first.size());
        assertEquals(50, first.stream().map(node -> node.x() + ":" + node.y()).distinct().count());
    }

    @Test
    void placesParentsInterfacesAndCompositeWholesBeforeTheirDependents() {
        List<ClassifierData> classifiers = List.of(
                classifier("parent", "Parent", "CLASS"),
                classifier("child", "Child", "CLASS"),
                classifier("contract", "Contract", "INTERFACE"),
                classifier("client", "Client", "CLASS"),
                classifier("whole", "Whole", "CLASS"),
                classifier("part", "Part", "CLASS"));
        GeneralizationData generalization = new GeneralizationData("g", "child", "parent", true);
        RealizationData realization = new RealizationData("r", "client", "contract");
        AssociationData composition = new AssociationData("a", "contains", null,
                end("whole-end", "whole", "COMPOSITE"), end("part-end", "part", "NONE"));
        XmiImportData data = data(classifiers, List.of(composition),
                List.of(generalization), List.of(realization));

        Map<String, ImportedClassDiagramCreator.NodePlacement> placements = creator.layout(data).stream()
                .collect(Collectors.toMap(ImportedClassDiagramCreator.NodePlacement::classifierExternalId,
                        Function.identity()));

        assertTrue(placements.get("parent").y() < placements.get("child").y());
        assertTrue(placements.get("contract").y() < placements.get("client").y());
        assertTrue(placements.get("whole").y() < placements.get("part").y());
    }

    private XmiImportData data(List<ClassifierData> classifiers, List<AssociationData> associations,
            List<GeneralizationData> generalizations, List<RealizationData> realizations) {
        return new XmiImportData("Imported", null, List.of(), classifiers, List.of(), List.of(), List.of(),
                associations, generalizations, realizations, List.of());
    }

    private ClassifierData classifier(String id, String name, String kind) {
        return new ClassifierData(id, name, null, kind, "PUBLIC", false, false);
    }

    private AssociationEndData end(String id, String classifierId, String aggregation) {
        return new AssociationEndData(id, null, classifierId, "PUBLIC", 1, 1,
                false, aggregation, "ASSOCIATION");
    }
}
