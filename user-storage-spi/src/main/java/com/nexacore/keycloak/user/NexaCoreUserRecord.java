package com.nexacore.keycloak.user;

import java.util.List;

public record NexaCoreUserRecord(
        Long id,
        Long personId,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified,
        boolean enabled,
        String passwordHash,
        List<String> roles
) {
}
