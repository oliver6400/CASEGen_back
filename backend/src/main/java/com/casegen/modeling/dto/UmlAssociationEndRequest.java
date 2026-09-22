package com.casegen.modeling.dto;

import com.casegen.modeling.domain.AggregationKind;
import com.casegen.modeling.domain.AssociationEndOwnership;
import com.casegen.modeling.domain.Visibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UmlAssociationEndRequest(
        @Size(max = 180) String roleName,
        @NotNull UUID classifierId,
        Visibility visibility,
        @PositiveOrZero Integer lowerBound,
        @PositiveOrZero Integer upperBound,
        Boolean navigable,
        AggregationKind aggregation,
        AssociationEndOwnership ownership
) {
}
