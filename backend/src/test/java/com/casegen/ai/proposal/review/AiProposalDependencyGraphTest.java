package com.casegen.ai.proposal.review;

import static org.junit.jupiter.api.Assertions.*;

import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.Visibility;
import java.util.*;
import org.junit.jupiter.api.Test;

class AiProposalDependencyGraphTest {
    private final AiProposalDependencyGraph graph = new AiProposalDependencyGraph();

    @Test
    void dependenciesComeFirstAndTieBreakingIsDeterministic() {
        AiReviewItem relationship = item("rel", AiReviewProposalType.ADD_ASSOCIATION);
        AiReviewItem classifier = item("class", AiReviewProposalType.ADD_CLASSIFIER);
        AiReviewItem attribute = item("attr", AiReviewProposalType.ADD_ATTRIBUTE);
        Map<String, Set<String>> dependencies = Map.of(
                "rel", Set.of("class"), "class", Set.of(), "attr", Set.of("class"));

        assertEquals(List.of("class", "attr", "rel"),
                graph.topologicalOrder(List.of(relationship, classifier, attribute), dependencies));
    }

    @Test
    void dependencyCycleIsRejectedDeterministically() {
        var exception = assertThrows(AiReviewValidationException.class, () ->
                graph.topologicalOrder(List.of(item("a", AiReviewProposalType.ADD_CLASSIFIER),
                                item("b", AiReviewProposalType.ADD_CLASSIFIER)),
                        Map.of("a", Set.of("b"), "b", Set.of("a"))));
        assertEquals(AiReviewValidationErrorCode.PROPOSAL_DEPENDENCY_CYCLE, exception.getCode());
    }

    @Test
    void diamondAndIndependentNodesHaveStableTopologicalOrder() {
        List<AiReviewItem> items=List.of(item("d",AiReviewProposalType.ADD_ASSOCIATION),
                item("c",AiReviewProposalType.ADD_ATTRIBUTE),item("b",AiReviewProposalType.ADD_CLASSIFIER),
                item("a",AiReviewProposalType.ADD_CLASSIFIER),item("independent",AiReviewProposalType.ADD_OPERATION));
        Map<String,Set<String>> deps=Map.of("a",Set.of(),"b",Set.of(),"c",Set.of("a"),
                "d",Set.of("a","b"),"independent",Set.of());
        List<String> first=graph.topologicalOrder(items,deps);
        List<String> second=graph.topologicalOrder(items,deps);
        assertEquals(first,second);
        assertTrue(first.indexOf("a")<first.indexOf("c"));
        assertTrue(first.indexOf("a")<first.indexOf("d"));
        assertTrue(first.indexOf("b")<first.indexOf("d"));
    }

    @Test
    void simpleChainAlwaysPlacesDependenciesFirst() {
        List<AiReviewItem> items=List.of(item("c",AiReviewProposalType.ADD_ASSOCIATION),
                item("b",AiReviewProposalType.ADD_ATTRIBUTE),item("a",AiReviewProposalType.ADD_CLASSIFIER));
        assertEquals(List.of("a","b","c"),graph.topologicalOrder(items,
                Map.of("a",Set.of(),"b",Set.of("a"),"c",Set.of("b"))));
    }

    private AiReviewItem item(String ref, AiReviewProposalType type) {
        AiReviewPayload payload = new ReviewAddClassifierPayload("X" + ref, null, null,
                ClassifierKind.CLASS, Visibility.PUBLIC, false, false);
        return new AiReviewItem(ref, type, ref, "reason", List.of(), payload);
    }
}
