# NexaCore Keycloak User Storage SPI Implementation Details

## Purpose

`keycloak2/user-storage-spi` is a standalone Keycloak User Storage SPI provider. It allows Keycloak to federate and authenticate existing NexaCore backend users from the auth module database instead of requiring duplicate users inside Keycloak.

This implementation is intentionally read-only from Keycloak's point of view:

- NexaCore remains the source of truth for users, passwords, enabled status, email verification, and local roles.
- Keycloak can look up users, search users, count users, and validate password credentials.
- Keycloak cannot update username, email, email verification, or enabled status through this adapter.

Provider id:

```text
nexacore-authmodule-user-storage
```

## Project Layout

```text
keycloak2/user-storage-spi
├── pom.xml
├── build-provider.sh
└── src/main
    ├── java/com/nexacore/keycloak/user
    │   ├── NexaCoreUserAdapter.java
    │   ├── NexaCoreUserRecord.java
    │   ├── NexaCoreUserRepository.java
    │   ├── NexaCoreUserStorageProvider.java
    │   └── NexaCoreUserStorageProviderFactory.java
    └── resources/META-INF/services/org.keycloak.storage.UserStorageProviderFactory
```

The Maven artifact is:

```text
com.nexacore:nexacore-keycloak-user-storage-spi:1.0.0
```

The provider is compiled for Java 21 and Keycloak 26.0.0.

## Runtime Dependencies

The provider jar is shaded with runtime dependencies needed by the SPI:

- PostgreSQL JDBC driver `org.postgresql:postgresql:42.7.4`
- Spring Security Crypto `org.springframework.security:spring-security-crypto:6.3.4`

Keycloak SPI dependencies are marked as `provided` because Keycloak supplies them at runtime:

- `keycloak-server-spi`
- `keycloak-server-spi-private`
- `keycloak-services`

## Keycloak SPI Registration

Keycloak discovers the provider through Java service loading:

```text
src/main/resources/META-INF/services/org.keycloak.storage.UserStorageProviderFactory
```

The file contains:

```text
com.nexacore.keycloak.user.NexaCoreUserStorageProviderFactory
```

At startup, Keycloak loads the factory and exposes a User Federation provider option with id:

```text
nexacore-authmodule-user-storage
```

## Provider Configuration

`NexaCoreUserStorageProviderFactory` defines the admin-console configuration fields:

| Key | Label | Default |
| --- | --- | --- |
| `jdbcUrl` | JDBC URL | `jdbc:postgresql://host.docker.internal:5433/auth_db` |
| `dbUsername` | DB username | `postgres` |
| `dbPassword` | DB password | none |

The factory loads the PostgreSQL JDBC driver in `init()`:

```java
Class.forName("org.postgresql.Driver");
```

When Keycloak creates a provider instance, the factory reads the configured JDBC settings from the `ComponentModel`, creates a `NexaCoreUserRepository`, and passes it into `NexaCoreUserStorageProvider`.

## Database Contract

The SPI reads these NexaCore auth tables:

```text
auth_users
auth_roles
auth_user_roles
kyc_person
```

The current SQL expects these columns:

```text
auth_users.id
auth_users.person_id
auth_users.username
auth_users.email
auth_users.email_verified
auth_users.enabled
auth_users.password

auth_roles.id
auth_roles.name

auth_user_roles.user_id
auth_user_roles.role_id

kyc_person.id
kyc_person.email
kyc_person.first_name
kyc_person.last_name
kyc_person.email_verified
```

The user query projects `email_verified` with a false fallback:

```sql
COALESCE(u.email_verified, false) AS email_verified
```

Password validation expects `auth_users.password` to contain a BCrypt hash compatible with Spring Security's `BCryptPasswordEncoder`.

## User Model Mapping

`NexaCoreUserRecord` is the internal immutable record passed from JDBC to Keycloak:

```text
id
personId
username
email
firstName
lastName
emailVerified
enabled
passwordHash
roles
```

`NexaCoreUserAdapter` maps the record into Keycloak's `UserModel`.

Mapped Keycloak fields:

| Keycloak field | NexaCore source |
| --- | --- |
| user id | Keycloak federated storage id whose external id is `nexacore:` + `auth_users.id` |
| username | `auth_users.username` |
| email | `kyc_person.email`, falling back to `auth_users.email` |
| first name | `kyc_person.first_name` |
| last name | `kyc_person.last_name` |
| email verified | `kyc_person.email_verified`, falling back to `auth_users.email_verified` |
| enabled | `auth_users.enabled` |

Mapped Keycloak attributes:

| Attribute | Value |
| --- | --- |
| `nexacore_user_id` | NexaCore user id as string |
| `nexacore_person_id` | NexaCore person id as string when `auth_users.person_id` is set |
| `nexacore_roles` | role names from `auth_roles.name` |

The adapter rejects username and enabled-status writes because those values are owned by NexaCore. It allows Keycloak profile writes for email and email verification through Keycloak federated storage so required actions such as **Update Account Information** can complete without trying to update the NexaCore auth database directly.

`auth_users` intentionally has no first-name or last-name columns. Those fields are owned by `kyc_db.kyc_person`. If Keycloak allows a temporary profile edit, the adapter can persist it in Keycloak federated storage, but NexaCore remains the authoritative source through the KYC business flow.

The adapter exposes `email`, `firstName`, `lastName`, and `emailVerified` through both the `UserModel` getters and the Keycloak attribute API. This is required because Keycloak profile validation and protocol mappers may read standard profile fields through attributes instead of only through direct getters.

## Lookup Behavior

`NexaCoreUserStorageProvider` implements:

- `UserStorageProvider`
- `UserLookupProvider`
- `UserQueryProvider`
- `CredentialInputValidator`

Supported lookups:

| Keycloak call | Database behavior |
| --- | --- |
| `getUserById` | Parses Keycloak storage id or `nexacore:` id and queries `auth_users.id` |
| `getUserByUsername` | Case-insensitive lookup against `auth_users.username` |
| `getUserByEmail` | Case-insensitive lookup against `auth_users.email` |
| `searchForUserStream` | Case-insensitive `LIKE` search on username or email |
| `searchForUserByUserAttributeStream` | Supports `nexacore_user_id` only |
| `getUsersCount` | Counts all users or filtered username/email matches |
| `getGroupMembersStream` | Returns an empty stream |

Search pagination uses `OFFSET` and `LIMIT`. If Keycloak does not pass pagination values, the repository defaults to offset `0` and limit `100`.

## Authentication Flow

When a federated NexaCore user logs in through Keycloak:

1. Keycloak locates the user by username or email through the SPI.
2. Keycloak calls `isConfiguredFor()` for the password credential type.
3. Keycloak calls `isValid()` with the submitted password.
4. The SPI reloads the user by username.
5. The SPI rejects authentication if the user is disabled, has no password hash, or has a blank password hash.
6. The SPI validates the submitted password with `BCryptPasswordEncoder.matches(rawPassword, storedHash)`.
7. Keycloak continues its normal OIDC flow if validation succeeds.

The raw submitted password is only used in memory during `isValid()` and is not logged by this provider.

## Build

From the project root:

```bash
cd keycloak2/user-storage-spi
./build-provider.sh
```

The script runs:

```bash
mvn -q -DskipTests package
```

Expected jar:

```text
keycloak2/user-storage-spi/target/nexacore-keycloak-user-storage-spi-1.0.0.jar
```

## Local Docker Run

Build the provider jar first. Then start Keycloak with:

```bash
cd keycloak2
./run-keycloak-in-docker.sh
```

If the jar exists, the script mounts it into Keycloak:

```text
/opt/keycloak/providers/nexacore-keycloak-user-storage-spi.jar
```

Default Keycloak settings:

| Setting | Default |
| --- | --- |
| URL | `http://localhost:9200` |
| Container | `nexacore-keycloak` |
| Image | `quay.io/keycloak/keycloak:26.0` |
| Admin username | `admin` |
| Admin password | `admin` |
| Keycloak DB | `jdbc:postgresql://host.docker.internal:5433/keycloak` |

The script connects Keycloak itself to the separate `keycloak` database. The SPI provider configuration should point to the NexaCore auth database, usually `auth_db`.

## Keycloak Admin Setup

1. Open `http://localhost:9200/admin`.
2. Select or create the `kyc` realm.
3. Go to **User federation**.
4. Add provider `nexacore-authmodule-user-storage`.
5. Configure:

```text
JDBC URL: jdbc:postgresql://host.docker.internal:5433/auth_db
DB username: postgres
DB password: 123456
```

6. Save the provider.
7. Test lookup from the Keycloak users screen.
8. Test login through the configured OIDC client.

## Relationship To Backend SSO Plan

This SPI changes Keycloak's user source. It does not replace the backend SSO token bridge described in `KEYCLOAK_SSO_IMPLEMENTATION_PLAN.md`.

Recommended combined flow:

1. User logs in at Keycloak.
2. Keycloak authenticates the user through this SPI against NexaCore `auth_users`.
3. Frontend receives a Keycloak token through Authorization Code + PKCE.
4. Frontend sends the Keycloak token to backend `/auth/sso/authenticate`.
5. Backend validates the Keycloak token.
6. Backend maps the Keycloak subject or attributes back to the local NexaCore user.
7. Backend issues the existing NexaCore application JWT.
8. Existing NexaCore privilege checks continue to use backend auth-module data.

For reliable backend mapping, include one of these values in the Keycloak token:

- `nexacore_person_id`
- `nexacore_user_id`
- username
- email

The current SPI exposes `nexacore_person_id` and `nexacore_user_id` as user attributes. Keycloak protocol mappers are needed if the backend should receive those attributes as token claims.

## Current Limitations

- The provider is read-only; Keycloak cannot create or update NexaCore users.
- It supports password validation only.
- It does not implement group membership.
- It exposes roles as the `nexacore_roles` user attribute, but does not map them to Keycloak realm roles or client roles by itself.
- It opens a new JDBC connection per query through `DriverManager`; there is no connection pool.
- SQL table and column names are hard-coded.
- Database errors are wrapped in `IllegalStateException`, which causes the Keycloak operation to fail.
- There are no unit or integration tests in the SPI project yet.

## Operational Notes

- Keep the Keycloak internal database separate from the NexaCore application databases.
- Do not point Keycloak's own `KC_DB_URL` at `auth_db`; only this SPI should read `auth_db`.
- Use a database user with the narrowest practical permissions for the SPI. Read-only access to `auth_users`, `auth_roles`, and `auth_user_roles` is sufficient for the current implementation.
- Avoid logging raw passwords, access tokens, refresh tokens, authorization headers, or full user payloads when debugging Keycloak.
- Rebuild and restart Keycloak after changing the provider jar.

## Verification Checklist

- `./build-provider.sh` creates the shaded provider jar.
- Keycloak starts and logs do not show provider loading errors.
- User Federation lists `nexacore-authmodule-user-storage`.
- Keycloak can search users from `auth_users`.
- Disabled NexaCore users cannot authenticate.
- Users with blank or missing password hashes cannot authenticate.
- Valid BCrypt passwords authenticate successfully.
- Keycloak tokens contain the claims needed by the backend SSO bridge, especially `nexacore_user_id` if backend mapping depends on it.
