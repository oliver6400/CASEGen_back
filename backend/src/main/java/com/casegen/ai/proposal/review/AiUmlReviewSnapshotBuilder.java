package com.casegen.ai.proposal.review;

import com.casegen.modeling.domain.*;
import com.casegen.modeling.repository.*;
import com.casegen.project.repository.ProjectRepository;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AiUmlReviewSnapshotBuilder {
    static final int PACKAGE_LIMIT=100, CLASSIFIER_LIMIT=300, PROPERTY_LIMIT=1000,
            OPERATION_LIMIT=1000, RELATIONSHIP_LIMIT=1000;
    private final ProjectRepository projects; private final UmlModelRepository models;
    private final DiagramRepository diagrams; private final DiagramNodeRepository nodes;
    private final DiagramEdgeRepository edges; private final UmlPackageRepository packages;
    private final UmlClassifierRepository classifiers; private final UmlPropertyRepository properties;
    private final UmlOperationRepository operations; private final UmlAssociationRepository associations;
    private final UmlAssociationEndRepository associationEnds; private final UmlGeneralizationRepository generalizations;
    private final UmlRealizationRepository realizations;

    public AiUmlReviewSnapshotBuilder(ProjectRepository projects,UmlModelRepository models,
            DiagramRepository diagrams,DiagramNodeRepository nodes,DiagramEdgeRepository edges,
            UmlPackageRepository packages,UmlClassifierRepository classifiers,UmlPropertyRepository properties,
            UmlOperationRepository operations,UmlAssociationRepository associations,
            UmlAssociationEndRepository associationEnds,UmlGeneralizationRepository generalizations,
            UmlRealizationRepository realizations){this.projects=projects;this.models=models;this.diagrams=diagrams;
        this.nodes=nodes;this.edges=edges;this.packages=packages;this.classifiers=classifiers;
        this.properties=properties;this.operations=operations;this.associations=associations;
        this.associationEnds=associationEnds;this.generalizations=generalizations;this.realizations=realizations;}

    @Transactional(readOnly=true)
    public AiUmlReviewSnapshot build(UUID projectId,UUID modelId,UUID diagramId){
        var project=projects.findById(projectId).orElseThrow(()->new IllegalArgumentException("project does not exist"));
        var model=models.findById(modelId).orElseThrow(()->new IllegalArgumentException("model does not exist"));
        if(!model.getProject().getId().equals(projectId))throw new IllegalArgumentException("model belongs to another project");
        Diagram diagram=null;Set<UUID> visibleElements=new HashSet<>(),visibleRelationships=new HashSet<>();
        if(diagramId!=null){diagram=diagrams.findById(diagramId).orElseThrow(()->new IllegalArgumentException("diagram does not exist"));
            if(!diagram.getModel().getId().equals(modelId))throw new IllegalArgumentException("diagram belongs to another model");
            nodes.findByDiagramIdOrderByZIndexAscCreatedAtAsc(diagramId).forEach(n->visibleElements.add(n.getUmlElement().getId()));
            edges.findByDiagramIdOrderByCreatedAtAsc(diagramId).forEach(e->visibleRelationships.add(e.getUmlElement().getId()));}

        var allPackages=packages.findByElementModelIdOrderByElementNameAsc(modelId);
        var allClassifiers=classifiers.findByElementModelIdOrderByElementNameAsc(modelId);
        List<UmlProperty> allProperties=new ArrayList<>();List<UmlOperation> allOperations=new ArrayList<>();
        for(var classifier:allClassifiers){allProperties.addAll(properties.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(classifier.getElementId()));allOperations.addAll(operations.findByOwnerClassifierElementIdOrderByElementNameAscElementIdAsc(classifier.getElementId()));}
        var allAssociations=associations.findByElementModelIdOrderByElementCreatedAtAsc(modelId);
        var allGeneralizations=generalizations.findByElementModelIdOrderByElementCreatedAtAsc(modelId);
        var allRealizations=realizations.findByElementModelIdOrderByElementCreatedAtAsc(modelId);
        int relationshipCount=allAssociations.size()+allGeneralizations.size()+allRealizations.size();
        boolean truncated=allPackages.size()>PACKAGE_LIMIT||allClassifiers.size()>CLASSIFIER_LIMIT
                ||allProperties.size()>PROPERTY_LIMIT||allOperations.size()>OPERATION_LIMIT||relationshipCount>RELATIONSHIP_LIMIT;
        var packageEntries=allPackages.stream().limit(PACKAGE_LIMIT).map(p->new AiUmlReviewSnapshot.PackageEntry(p.getElementId(),p.getElement().getName(),p.getElement().getDocumentation(),p.getParentPackage()==null?null:p.getParentPackage().getElementId())).toList();
        Set<UUID> includedClassifiers=new HashSet<>();
        var classifierEntries=allClassifiers.stream().limit(CLASSIFIER_LIMIT).peek(c->includedClassifiers.add(c.getElementId())).map(c->new AiUmlReviewSnapshot.ClassifierEntry(c.getElementId(),c.getElement().getName(),c.getElement().getDocumentation(),c.getUmlPackage()==null?null:c.getUmlPackage().getElementId(),c.getKind(),c.getVisibility(),c.isAbstractClassifier(),c.isActive(),visibleElements.contains(c.getElementId()))).toList();
        var propertyEntries=allProperties.stream().filter(p->includedClassifiers.contains(p.getOwnerClassifier().getElementId())).limit(PROPERTY_LIMIT).map(p->new AiUmlReviewSnapshot.PropertyEntry(p.getElementId(),p.getOwnerClassifier().getElementId(),p.getElement().getName(),p.getElement().getDocumentation(),p.getVisibility(),p.getTypeKind(),p.getPrimitiveType(),p.getTypeClassifier()==null?null:p.getTypeClassifier().getElementId(),p.getLowerBound(),p.getUpperBound(),p.isStaticProperty(),p.isReadOnly(),p.isDerived())).toList();
        var operationEntries=allOperations.stream().filter(o->includedClassifiers.contains(o.getOwnerClassifier().getElementId())).limit(OPERATION_LIMIT).map(o->new AiUmlReviewSnapshot.OperationEntry(o.getElementId(),o.getOwnerClassifier().getElementId(),o.getElement().getName(),o.getElement().getDocumentation(),o.getVisibility(),o.isAbstractOperation(),o.isStaticOperation(),o.isQuery())).toList();
        int remaining=RELATIONSHIP_LIMIT;
        List<AiUmlReviewSnapshot.AssociationEntry> associationEntries=new ArrayList<>();
        for(var a:allAssociations){if(remaining--<=0)break;var ends=associationEnds.findByAssociationElementIdOrderByMemberOrderAsc(a.getElementId());if(ends.size()!=2)continue;associationEntries.add(new AiUmlReviewSnapshot.AssociationEntry(a.getElementId(),a.getElement().getName(),a.getElement().getDocumentation(),a.getUmlPackage()==null?null:a.getUmlPackage().getElementId(),end(ends.get(0)),end(ends.get(1)),visibleRelationships.contains(a.getElementId())));}
        List<AiUmlReviewSnapshot.GeneralizationEntry> generalizationEntries=new ArrayList<>();
        for(var g:allGeneralizations){if(remaining--<=0)break;generalizationEntries.add(new AiUmlReviewSnapshot.GeneralizationEntry(g.getElementId(),g.getSpecificClassifier().getElementId(),g.getGeneralClassifier().getElementId(),g.isSubstitutable(),g.getElement().getDocumentation(),visibleRelationships.contains(g.getElementId())));}
        List<AiUmlReviewSnapshot.RealizationEntry> realizationEntries=new ArrayList<>();
        for(var r:allRealizations){if(remaining--<=0)break;realizationEntries.add(new AiUmlReviewSnapshot.RealizationEntry(r.getElementId(),r.getClientClassifier().getElementId(),r.getSupplierClassifier().getElementId(),r.getElement().getDocumentation(),visibleRelationships.contains(r.getElementId())));}
        var diagramContext=diagram==null?null:new AiUmlReviewSnapshot.DiagramContext(diagram.getId(),diagram.getName(),diagram.getRevision(),visibleElements.stream().sorted().toList(),visibleRelationships.stream().sorted().toList());
        return new AiUmlReviewSnapshot(projectId,project.getName(),modelId,model.getName(),model.getRevision(),diagramContext,truncated,new AiUmlReviewSnapshot.Counts(allPackages.size(),allClassifiers.size(),allProperties.size(),allOperations.size(),relationshipCount),packageEntries,classifierEntries,propertyEntries,operationEntries,associationEntries,generalizationEntries,realizationEntries);
    }
    private AiUmlReviewSnapshot.AssociationEndEntry end(UmlAssociationEnd e){var p=e.getProperty();return new AiUmlReviewSnapshot.AssociationEndEntry(p.getTypeClassifier().getElementId(),p.getElement().getName(),p.getLowerBound(),p.getUpperBound(),e.isNavigable(),e.getAggregation(),e.getOwnership());}
}
