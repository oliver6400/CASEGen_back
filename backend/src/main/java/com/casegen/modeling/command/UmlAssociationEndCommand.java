package com.casegen.modeling.command;

import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.Visibility;
import java.util.UUID;

public record UmlAssociationEndCommand(
        String roleName,
        UUID classifierId,
        Visibility visibility,
        Integer lowerBound,
        Integer upperBound,
        boolean navigable,
        AggregationKind aggregation,
        AssociationEndOwnership ownership
) {
}
