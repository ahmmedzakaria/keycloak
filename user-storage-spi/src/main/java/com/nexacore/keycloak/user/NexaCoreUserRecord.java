package com.nexacore.keycloak.user;

import java.util.List;

public record NexaCoreUserRecord(
        Long id,
        String username,
        String email,
        boolean emailVerified,
        boolean enabled,
        String passwordHash,
        List<String> roles
) {
}
