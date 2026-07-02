# NexaCore Keycloak User Storage SPI

This is a separate Keycloak provider jar for federating users from the NexaCore `auth_db` tables used by `authmodule.core.entity.User`.

Provider id:

```text
nexacore-authmodule-user-storage
```

## What It Reads

The provider reads:

- `auth_users`
- `auth_roles`
- `auth_user_roles`

It exposes these values to Keycloak:

- `username`
- `email`
- `email_verified`
- `enabled`
- `nexacore_user_id` user attribute
- `nexacore_roles` user attribute

It validates local passwords against the BCrypt hash stored in `auth_users.password`.

## Build

```bash
cd keycloak/user-storage-spi
./build-provider.sh
```

The jar is created at:

```text
keycloak/user-storage-spi/target/nexacore-keycloak-user-storage-spi-1.0.0.jar
```

## Run With Local Docker Keycloak

Build the provider jar first, then start Keycloak with:

```bash
./keycloak/run-keycloak-in-docker.sh
```

The run script automatically mounts the provider jar into:

```text
/opt/keycloak/providers/nexacore-keycloak-user-storage-spi.jar
```

## Configure In Keycloak

1. Open `http://localhost:9200/admin`.
2. Select the `kyc` realm.
3. Go to **User federation**.
4. Add provider: `nexacore-authmodule-user-storage`.
5. Use these settings:

```text
JDBC URL: jdbc:postgresql://host.docker.internal:5433/auth_db
DB username: postgres
DB password: 123456
```

After saving, Keycloak can authenticate users from NexaCore `auth_db` using their existing local username/password.
