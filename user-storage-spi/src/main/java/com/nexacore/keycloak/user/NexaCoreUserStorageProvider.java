package com.nexacore.keycloak.user;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.stream.Stream;

public class NexaCoreUserStorageProvider implements UserStorageProvider,
        UserLookupProvider,
        UserQueryProvider,
        CredentialInputValidator {

    private final KeycloakSession session;
    private final ComponentModel model;
    private final NexaCoreUserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public NexaCoreUserStorageProvider(KeycloakSession session,
                                       ComponentModel model,
                                       NexaCoreUserRepository userRepository) {
        this.session = session;
        this.model = model;
        this.userRepository = userRepository;
    }

    @Override
    public void close() {
        // JDBC connections are opened per query and closed immediately.
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String externalId = StorageId.externalId(id);
        if (externalId == null) {
            externalId = id;
        }
        if (externalId.startsWith(NexaCoreUserAdapter.STORAGE_ID_PREFIX)) {
            externalId = externalId.substring(NexaCoreUserAdapter.STORAGE_ID_PREFIX.length());
        }

        try {
            long userId = Long.parseLong(externalId);
            return userRepository.findById(userId)
                    .map(user -> toAdapter(realm, user))
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        return userRepository.findByUsername(username)
                .map(user -> toAdapter(realm, user))
                .orElse(null);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return userRepository.findByEmail(email)
                .map(user -> toAdapter(realm, user))
                .orElse(null);
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType()) || input.getChallengeResponse() == null) {
            return false;
        }

        return userRepository.findByUsername(user.getUsername())
                .filter(NexaCoreUserRecord::enabled)
                .map(NexaCoreUserRecord::passwordHash)
                .filter(hash -> hash != null && !hash.isBlank())
                .map(hash -> passwordEncoder.matches(input.getChallengeResponse(), hash))
                .orElse(false);
    }

    @Override
    public int getUsersCount(RealmModel realm) {
        return userRepository.count();
    }

    @Override
    public int getUsersCount(RealmModel realm, String search) {
        return userRepository.countSearch(search);
    }

    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params) {
        String search = params == null ? "" : params.getOrDefault(UserModel.SEARCH, "");
        return userRepository.countSearch(search);
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, String search) {
        return searchForUserStream(realm, search, 0, 100);
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm,
                                                 String search,
                                                 Integer firstResult,
                                                 Integer maxResults) {
        return userRepository.search(search, firstResult, maxResults)
                .stream()
                .map(user -> toAdapter(realm, user));
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm,
                                                 Map<String, String> params,
                                                 Integer firstResult,
                                                 Integer maxResults) {
        String search = params == null ? "" : params.getOrDefault(UserModel.SEARCH, "");
        if ((search == null || search.isBlank()) && params != null) {
            search = firstNonBlank(params.get(UserModel.USERNAME), params.get(UserModel.EMAIL));
        }
        return searchForUserStream(realm, search, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm,
                                                   GroupModel group,
                                                   Integer firstResult,
                                                   Integer maxResults) {
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm,
                                                                String attrName,
                                                                String attrValue) {
        if ("nexacore_user_id".equals(attrName)) {
            try {
                return userRepository.findById(Long.parseLong(attrValue))
                        .stream()
                        .map(user -> toAdapter(realm, user));
            } catch (NumberFormatException e) {
                return Stream.empty();
            }
        }
        return Stream.empty();
    }

    private NexaCoreUserAdapter toAdapter(RealmModel realm, NexaCoreUserRecord user) {
        return new NexaCoreUserAdapter(session, realm, model, user);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
