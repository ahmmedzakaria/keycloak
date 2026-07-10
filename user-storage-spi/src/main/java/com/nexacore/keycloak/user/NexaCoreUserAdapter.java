package com.nexacore.keycloak.user;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
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
        return StorageId.keycloakId(storageProviderModel, STORAGE_ID_PREFIX + user.id());
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
        return firstNonBlank(user.email(), super.getFirstAttribute(UserModel.EMAIL));
    }

    @Override
    public void setEmail(String email) {
        super.setEmail(email);
    }

    @Override
    public boolean isEmailVerified() {
        return user.emailVerified();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        super.setEmailVerified(verified);
    }

    @Override
    public String getFirstName() {
        return firstNonBlank(user.firstName(), super.getFirstAttribute(UserModel.FIRST_NAME));
    }

    @Override
    public void setFirstName(String firstName) {
        super.setFirstName(firstName);
    }

    @Override
    public String getLastName() {
        return firstNonBlank(user.lastName(), super.getFirstAttribute(UserModel.LAST_NAME));
    }

    @Override
    public void setLastName(String lastName) {
        super.setLastName(lastName);
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
        putIfPresent(attributes, UserModel.EMAIL, getEmail());
        putIfPresent(attributes, UserModel.FIRST_NAME, getFirstName());
        putIfPresent(attributes, UserModel.LAST_NAME, getLastName());
        attributes.put(UserModel.EMAIL_VERIFIED, List.of(String.valueOf(isEmailVerified())));
        attributes.put("nexacore_user_id", List.of(String.valueOf(user.id())));
        if (user.personId() != null) {
            attributes.put("nexacore_person_id", List.of(String.valueOf(user.personId())));
        }
        attributes.put("nexacore_roles", user.roles());
        return attributes;
    }

    @Override
    public String getFirstAttribute(String name) {
        if (UserModel.EMAIL.equals(name)) {
            return firstNonBlank(user.email(), super.getFirstAttribute(UserModel.EMAIL));
        }
        if (UserModel.FIRST_NAME.equals(name)) {
            return firstNonBlank(user.firstName(), super.getFirstAttribute(UserModel.FIRST_NAME));
        }
        if (UserModel.LAST_NAME.equals(name)) {
            return firstNonBlank(user.lastName(), super.getFirstAttribute(UserModel.LAST_NAME));
        }
        if (UserModel.EMAIL_VERIFIED.equals(name)) {
            return String.valueOf(isEmailVerified());
        }
        if ("nexacore_user_id".equals(name)) {
            return String.valueOf(user.id());
        }
        if ("nexacore_person_id".equals(name) && user.personId() != null) {
            return String.valueOf(user.personId());
        }
        return super.getFirstAttribute(name);
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        if (UserModel.EMAIL.equals(name)) {
            return streamIfPresent(getEmail());
        }
        if (UserModel.FIRST_NAME.equals(name)) {
            return streamIfPresent(getFirstName());
        }
        if (UserModel.LAST_NAME.equals(name)) {
            return streamIfPresent(getLastName());
        }
        if (UserModel.EMAIL_VERIFIED.equals(name)) {
            return Stream.of(String.valueOf(isEmailVerified()));
        }
        if ("nexacore_user_id".equals(name)) {
            return Stream.of(String.valueOf(user.id()));
        }
        if ("nexacore_person_id".equals(name) && user.personId() != null) {
            return Stream.of(String.valueOf(user.personId()));
        }
        if ("nexacore_roles".equals(name)) {
            return user.roles().stream();
        }
        return super.getAttributeStream(name);
    }

    private void putIfPresent(Map<String, List<String>> attributes, String name, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(name, List.of(value));
        }
    }

    private Stream<String> streamIfPresent(String value) {
        return value == null || value.isBlank() ? Stream.empty() : Stream.of(value);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
