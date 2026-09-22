package com.casegen.modeling.dto;

import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.Visibility;
import java.util.UUID;

public record UmlAssociationEndResponse(
        UUID propertyId,
        String roleName,
        UUID classifierId,
        UUID ownerClassifierId,
        Visibility visibility,
        int lowerBound,
        Integer upperBound,
        boolean navigable,
        AggregationKind aggregation,
        AssociationEndOwnership ownership,
        short memberOrder
) {
}
