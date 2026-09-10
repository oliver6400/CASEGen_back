package com.casegen.auth.dto;

import com.casegen.auth.domain.SystemRole;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        String displayName,
        SystemRole systemRole
) {
}
