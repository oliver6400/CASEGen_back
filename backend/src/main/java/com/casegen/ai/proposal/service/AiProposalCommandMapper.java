package com.casegen.ai.proposal.service;

import com.casegen.ai.proposal.domain.AiProposalType;
import com.casegen.ai.proposal.payload.AddAssociationPayload;
import com.casegen.ai.proposal.payload.AddAttributePayload;
import com.casegen.ai.proposal.payload.AddClassifierPayload;
import com.casegen.ai.proposal.payload.AddGeneralizationPayload;
import com.casegen.ai.proposal.payload.AddOperationPayload;
import com.casegen.ai.proposal.payload.AddRealizationPayload;
import com.casegen.ai.proposal.payload.AiProposalPayload;
import com.casegen.ai.proposal.payload.AssociationEndProposalPayload;
import com.casegen.modeling.command.CreateUmlAssociationCommand;
import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlGeneralizationCommand;
import com.casegen.modeling.command.CreateUmlOperationCommand;
import com.casegen.modeling.command.CreateUmlPropertyCommand;
import com.casegen.modeling.command.CreateUmlRealizationCommand;
import com.casegen.modeling.command.UmlAssociationEndCommand;
import com.casegen.modeling.service.UmlModelService;
import com.casegen.modeling.service.UmlRelationshipService;
import com.casegen.ai.proposal.domain.AiProposal;
import com.casegen.ai.proposal.domain.AiProposalDependency;
import com.casegen.ai.proposal.review.*;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.repository.UmlClassifierRepository;
import com.casegen.modeling.repository.UmlPropertyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class AiProposalCommandMapper {
    private final UmlModelService models;
    private final UmlRelationshipService relationships;
    private final AiProposalBatchReferenceResolver references;
    private final UmlClassifierRepository classifiers;
    private final UmlPropertyRepository properties;

    @Autowired
    public AiProposalCommandMapper(UmlModelService models, UmlRelationshipService relationships,
            AiProposalBatchReferenceResolver references,
            UmlClassifierRepository classifiers, UmlPropertyRepository properties) {
        this.models = models;
        this.relationships = relationships;
        this.references = references;
        this.classifiers = classifiers;
        this.properties = properties;
    }

    AiProposalCommandMapper(UmlModelService models, UmlRelationshipService relationships) {
        this(models, relationships, null, null, null);
    }

    public AiProposalAppliedElement applyBatch(UUID modelId, AiProposal proposal,
            AiReviewPayload payload, List<AiProposalDependency> dependencies) {
        return switch (proposal.getProposalType()) {
            case ADD_CLASSIFIER -> {
                var p = (ReviewAddClassifierPayload) payload;
                yield addClassifier(modelId, new AddClassifierPayload(p.kind(), p.name(),
                        p.packageId(), p.visibility(), p.abstractClassifier(), p.active(),
                        p.documentation()));
            }
            case ADD_ATTRIBUTE -> {
                var p = (ReviewAddAttributePayload) payload;
                UUID owner = references.resolve(proposal, p.ownerRef(), dependencies,
                        UmlElementType.CLASSIFIER);
                UUID type = p.typeClassifierRef() == null ? null : references.resolve(proposal,
                        p.typeClassifierRef(), dependencies, UmlElementType.CLASSIFIER);
                var response = models.createProperty(modelId, owner,
                        new CreateUmlPropertyCommand(p.name(), p.documentation(), p.visibility(),
                                p.typeKind(), p.primitiveType(), type, p.lowerBound(), p.upperBound(),
                                p.staticProperty(), p.readOnly(), p.derived()));
                yield new AiProposalAppliedElement(response.id(), response.modelRevision());
            }
            case ADD_OPERATION -> {
                var p = (ReviewAddOperationPayload) payload;
                UUID owner = references.resolve(proposal, p.ownerRef(), dependencies,
                        UmlElementType.CLASSIFIER);
                var response = models.createOperation(modelId, owner,
                        new CreateUmlOperationCommand(p.name(), p.documentation(), p.visibility(),
                                p.abstractOperation(), p.staticOperation(), p.query()));
                yield new AiProposalAppliedElement(response.id(), response.modelRevision());
            }
            case ADD_ASSOCIATION -> {
                var p = (ReviewAddAssociationPayload) payload;
                var first = reviewEnd(proposal, p.firstEnd(), dependencies);
                var second = reviewEnd(proposal, p.secondEnd(), dependencies);
                var response = relationships.createAssociation(modelId,
                        new CreateUmlAssociationCommand(p.name(), p.documentation(), p.packageId(),
                                end(first), end(second)));
                yield new AiProposalAppliedElement(response.id(), response.modelRevision());
            }
            case ADD_GENERALIZATION -> {
                var p = (ReviewAddGeneralizationPayload) payload;
                UUID specific = references.resolve(proposal, p.specificRef(), dependencies,
                        UmlElementType.CLASSIFIER);
                UUID general = references.resolve(proposal, p.generalRef(), dependencies,
                        UmlElementType.CLASSIFIER);
                yield addGeneralization(modelId, new AddGeneralizationPayload(specific, general,
                        p.substitutable(), p.documentation()));
            }
            case ADD_REALIZATION -> {
                var p = (ReviewAddRealizationPayload) payload;
                UUID client = references.resolve(proposal, p.clientClassifierRef(), dependencies,
                        UmlElementType.CLASSIFIER);
                UUID supplier = references.resolve(proposal, p.supplierRef(), dependencies,
                        UmlElementType.CLASSIFIER);
                yield addRealization(modelId, new AddRealizationPayload(client, supplier,
                        p.documentation()));
            }
            case UPDATE_CLASSIFIER -> updateClassifier(modelId,
                    (ReviewUpdateClassifierPayload) payload);
            case UPDATE_ATTRIBUTE -> updateAttribute(modelId, proposal,
                    (ReviewUpdateAttributePayload) payload, dependencies);
        };
    }

    private AssociationEndProposalPayload reviewEnd(AiProposal proposal,
            ReviewAssociationEndPayload end, List<AiProposalDependency> dependencies) {
        UUID classifier = references.resolve(proposal, end.classifierRef(), dependencies,
                UmlElementType.CLASSIFIER);
        return new AssociationEndProposalPayload(classifier, end.roleName(), end.lowerBound(),
                end.upperBound(), end.navigable(), end.aggregation(), end.ownership());
    }

    private AiProposalAppliedElement updateClassifier(UUID modelId,
            ReviewUpdateClassifierPayload p) {
        var classifier = classifiers.findById(p.targetClassifierId())
                .orElseThrow(() -> new AiProposalException(
                        AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                        "Target classifier no longer exists"));
        if (!classifier.getElement().getModel().getId().equals(modelId)) {
            throw new AiProposalException(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                    "Target classifier belongs to another model");
        }
        var response = models.updateClassifier(modelId, p.targetClassifierId(),
                new CreateUmlClassifierCommand(p.name(), p.documentation(), p.packageId(),
                        classifier.getKind(), p.visibility(), p.abstractClassifier(), p.active()));
        return new AiProposalAppliedElement(response.id(), response.modelRevision());
    }

    private AiProposalAppliedElement updateAttribute(UUID modelId, AiProposal proposal,
            ReviewUpdateAttributePayload p, List<AiProposalDependency> dependencies) {
        var property = properties.findById(p.targetPropertyId())
                .orElseThrow(() -> new AiProposalException(
                        AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                        "Target property no longer exists"));
        if (!property.getElement().getModel().getId().equals(modelId)) {
            throw new AiProposalException(AiProposalErrorCode.PROPOSAL_NO_LONGER_VALID,
                    "Target property belongs to another model");
        }
        UUID type = p.typeClassifierRef() == null ? null : references.resolve(proposal,
                p.typeClassifierRef(), dependencies, UmlElementType.CLASSIFIER);
        UUID owner = property.getOwnerClassifier().getElementId();
        var response = models.updateProperty(modelId, owner, p.targetPropertyId(),
                new CreateUmlPropertyCommand(p.name(), p.documentation(), p.visibility(),
                        p.typeKind(), p.primitiveType(), type, p.lowerBound(), p.upperBound(),
                        p.staticProperty(), p.readOnly(), p.derived()));
        return new AiProposalAppliedElement(response.id(), response.modelRevision());
    }

    public AiProposalAppliedElement apply(UUID modelId, AiProposalType type,
            AiProposalPayload payload) {
        return switch (type) {
            case ADD_CLASSIFIER -> addClassifier(modelId, (AddClassifierPayload) payload);
            case ADD_ATTRIBUTE -> addAttribute(modelId, (AddAttributePayload) payload);
            case ADD_OPERATION -> addOperation(modelId, (AddOperationPayload) payload);
            case ADD_ASSOCIATION -> addAssociation(modelId, (AddAssociationPayload) payload);
            case ADD_GENERALIZATION -> addGeneralization(modelId, (AddGeneralizationPayload) payload);
            case ADD_REALIZATION -> addRealization(modelId, (AddRealizationPayload) payload);
            case UPDATE_CLASSIFIER, UPDATE_ATTRIBUTE -> throw new AiProposalException(
                    AiProposalErrorCode.BATCH_PROPOSAL_TYPE_NOT_YET_APPLICABLE,
                    "Review update proposals are not applicable until B4-7E");
        };
    }

    private AiProposalAppliedElement addClassifier(UUID modelId, AddClassifierPayload value) {
        var response = models.createClassifier(modelId, new CreateUmlClassifierCommand(value.name(),
                value.documentation(), value.packageId(), value.kind(), value.visibility(),
                value.abstractClassifier(), value.active()));
        return new AiProposalAppliedElement(response.id(), response.modelRevision());
    }

    private AiProposalAppliedElement addAttribute(UUID modelId, AddAttributePayload value) {
        var response = models.createProperty(modelId, value.ownerClassifierId(),
                new CreateUmlPropertyCommand(value.name(), null, value.visibility(), value.typeKind(),
                        value.primitiveType(), value.typeClassifierId(), value.lowerBound(),
                        value.upperBound(), value.staticProperty(), value.readOnly(), value.derived()));
        return new AiProposalAppliedElement(response.id(), response.modelRevision());
    }

    private AiProposalAppliedElement addOperation(UUID modelId, AddOperationPayload value) {
        var response = models.createOperation(modelId, value.ownerClassifierId(),
                new CreateUmlOperationCommand(value.name(), null, value.visibility(),
                        value.abstractOperation(), value.staticOperation(), value.query()));
        return new AiProposalAppliedElement(response.id(), response.modelRevision());
    }

    private AiProposalAppliedElement addAssociation(UUID modelId, AddAssociationPayload value) {
        var response = relationships.createAssociation(modelId, new CreateUmlAssociationCommand(
                value.name(), null, value.packageId(), end(value.firstEnd()), end(value.secondEnd())));
        return new AiProposalAppliedElement(response.id(), response.modelRevision());
    }

    private AiProposalAppliedElement addGeneralization(UUID modelId, AddGeneralizationPayload value) {
        var response = relationships.createGeneralization(modelId,
                new CreateUmlGeneralizationCommand(value.specificClassifierId(),
                        value.generalClassifierId(), value.substitutable(), value.documentation()));
        return new AiProposalAppliedElement(response.id(), response.modelRevision());
    }

    private AiProposalAppliedElement addRealization(UUID modelId, AddRealizationPayload value) {
        var response = relationships.createRealization(modelId,
                new CreateUmlRealizationCommand(value.clientClassifierId(),
                        value.supplierClassifierId(), value.documentation()));
        return new AiProposalAppliedElement(response.id(), response.modelRevision());
    }

    private UmlAssociationEndCommand end(AssociationEndProposalPayload value) {
        return new UmlAssociationEndCommand(value.roleName(), value.classifierId(), null,
                value.lowerBound(), value.upperBound(), value.navigable(), value.aggregation(),
                value.ownership());
    }
}
