package com.casegen.admin.dto;

import com.casegen.auth.domain.SystemRole;
import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id, String email, String displayName, SystemRole systemRole, boolean enabled, Instant createdAt
) {
}
