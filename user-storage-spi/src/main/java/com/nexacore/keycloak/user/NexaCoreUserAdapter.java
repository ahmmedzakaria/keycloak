package com.nexacore.keycloak.user;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class NexaCoreUserAdapter extends AbstractUserAdapterFederatedStorage {

    public static final String STORAGE_ID_PREFIX = "nexacore:";

    private final NexaCoreUserRecord user;

    public NexaCoreUserAdapter(KeycloakSession session,
                               RealmModel realm,
                               ComponentModel storageProviderModel,
                               NexaCoreUserRecord user) {
        super(session, realm, storageProviderModel);
        this.user = user;
    }

    @Override
    public String getId() {
        return STORAGE_ID_PREFIX + user.id();
    }

    @Override
    public String getUsername() {
        return user.username();
    }

    @Override
    public void setUsername(String username) {
        throw new UnsupportedOperationException("NexaCore users are read-only in Keycloak");
    }

    @Override
    public String getEmail() {
        return user.email();
    }

    @Override
    public void setEmail(String email) {
        throw new UnsupportedOperationException("NexaCore users are read-only in Keycloak");
    }

    @Override
    public boolean isEmailVerified() {
        return user.emailVerified();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        throw new UnsupportedOperationException("NexaCore users are read-only in Keycloak");
    }

    @Override
    public boolean isEnabled() {
        return user.enabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        throw new UnsupportedOperationException("NexaCore users are read-only in Keycloak");
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> attributes = new HashMap<>(super.getAttributes());
        attributes.put("nexacore_user_id", List.of(String.valueOf(user.id())));
        attributes.put("nexacore_roles", user.roles());
        return attributes;
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        if ("nexacore_user_id".equals(name)) {
            return Stream.of(String.valueOf(user.id()));
        }
        if ("nexacore_roles".equals(name)) {
            return user.roles().stream();
        }
        return super.getAttributeStream(name);
    }
}
