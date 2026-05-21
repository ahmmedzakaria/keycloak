package com.nexacore.keycloak.user;

import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;

public class NexaCoreUserStorageProviderFactory implements UserStorageProviderFactory<NexaCoreUserStorageProvider> {

    public static final String PROVIDER_ID = "nexacore-authmodule-user-storage";

    private static final String CONFIG_JDBC_URL = "jdbcUrl";
    private static final String CONFIG_DB_USERNAME = "dbUsername";
    private static final String CONFIG_DB_PASSWORD = "dbPassword";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = List.of(
            configProperty(
                    CONFIG_JDBC_URL,
                    "JDBC URL",
                    "JDBC URL for NexaCore auth_db, for example jdbc:postgresql://host.docker.internal:5433/auth_db",
                    ProviderConfigProperty.STRING_TYPE,
                    "jdbc:postgresql://host.docker.internal:5433/auth_db"
            ),
            configProperty(
                    CONFIG_DB_USERNAME,
                    "DB username",
                    "Database username for NexaCore auth_db",
                    ProviderConfigProperty.STRING_TYPE,
                    "postgres"
            ),
            configProperty(
                    CONFIG_DB_PASSWORD,
                    "DB password",
                    "Database password for NexaCore auth_db",
                    ProviderConfigProperty.PASSWORD,
                    null
            )
    );

    @Override
    public NexaCoreUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        String jdbcUrl = model.get(CONFIG_JDBC_URL);
        String dbUsername = model.get(CONFIG_DB_USERNAME);
        String dbPassword = model.get(CONFIG_DB_PASSWORD);

        NexaCoreUserRepository userRepository = new NexaCoreUserRepository(jdbcUrl, dbUsername, dbPassword);
        return new NexaCoreUserStorageProvider(session, model, userRepository);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Federates users from NexaCore authmodule users table.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public void init(Config.Scope config) {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PostgreSQL JDBC driver is not available to Keycloak provider", e);
        }
    }

    private static ProviderConfigProperty configProperty(String name,
                                                         String label,
                                                         String helpText,
                                                         String type,
                                                         Object defaultValue) {
        ProviderConfigProperty property = new ProviderConfigProperty();
        property.setName(name);
        property.setLabel(label);
        property.setHelpText(helpText);
        property.setType(type);
        property.setDefaultValue(defaultValue);
        return property;
    }
}
