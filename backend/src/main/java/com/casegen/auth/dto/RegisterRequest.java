package com.casegen.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 120) String displayName
) {
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=[REDACTED], displayName=" + displayName + "]";
    }
}
