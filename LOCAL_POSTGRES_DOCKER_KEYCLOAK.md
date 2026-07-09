# Running Docker Keycloak With Local PostgreSQL

This setup runs Keycloak inside Docker while PostgreSQL runs directly on the local machine.

## How The Script Connects To Local PostgreSQL

`run-keycloak-in-docker.sh` is already configured for this model. It starts the Keycloak container with:

```bash
--add-host=host.docker.internal:host-gateway
```

That makes the host machine reachable from inside the Docker container as:

```text
host.docker.internal
```

So Keycloak can connect to local PostgreSQL with a JDBC URL like:

```text
jdbc:postgresql://host.docker.internal:5433/keycloak
```

## Two Databases Are Involved

There are two separate database connections in this setup.

## 1. Keycloak Internal Database

Keycloak uses its own database for realms, clients, sessions, users imported into Keycloak, and other Keycloak configuration.

The script defaults are:

```bash
KEYCLOAK_DB_HOST=host.docker.internal
KEYCLOAK_DB_PORT=5433
KEYCLOAK_DB_NAME=keycloak
KEYCLOAK_DB_USERNAME=postgres
KEYCLOAK_DB_PASSWORD=123456
```

These values produce:

```text
jdbc:postgresql://host.docker.internal:5433/keycloak
```

The `keycloak` database should exist in local PostgreSQL before starting Keycloak:

```sql
CREATE DATABASE keycloak;
```

## 2. NexaCore Auth Database For User Storage SPI

The User Storage SPI reads NexaCore users from the backend auth database. This is configured in the Keycloak Admin UI after Keycloak starts.

Use:

```text
JDBC URL: jdbc:postgresql://host.docker.internal:5433/auth_db
DB username: postgres
DB password: 123456
```

The SPI reads:

```text
auth_users
auth_roles
auth_user_roles
```

Do not point Keycloak's own internal database setting at `auth_db`. Keycloak should use the separate `keycloak` database, while the SPI provider reads `auth_db`.

## PostgreSQL Requirements

Local PostgreSQL must accept TCP connections from the Docker container.

Check that PostgreSQL is listening on the expected port:

```bash
ss -ltnp | grep 5433
```

If Docker cannot connect, check `postgresql.conf`:

```conf
listen_addresses = '*'
```

For local development, `pg_hba.conf` also needs to allow connections from the Docker bridge network. A common rule is:

```conf
host    all     all     172.17.0.0/16     md5
```

Restart PostgreSQL after changing `postgresql.conf` or `pg_hba.conf`.

## Run Steps

Build the User Storage SPI jar:

```bash
cd keycloak2/user-storage-spi
./build-provider.sh
```

Start Keycloak:

```bash
cd ..
./run-keycloak-in-docker.sh
```

Open Keycloak:

```text
http://localhost:9200/admin
```

Default admin credentials from the script:

```text
username: admin
password: admin
```

Then configure User Federation:

```text
Provider: nexacore-authmodule-user-storage
JDBC URL: jdbc:postgresql://host.docker.internal:5433/auth_db
DB username: postgres
DB password: 123456
```

## If PostgreSQL Uses Port 5432

If local PostgreSQL runs on the default port `5432`, start Keycloak with:

```bash
KEYCLOAK_DB_PORT=5432 ./run-keycloak-in-docker.sh
```

Then configure the SPI JDBC URL as:

```text
jdbc:postgresql://host.docker.internal:5432/auth_db
```

## Useful Troubleshooting

View Keycloak logs:

```bash
docker logs -f nexacore-keycloak
```

Remove and restart the container:

```bash
docker rm -f nexacore-keycloak
./run-keycloak-in-docker.sh
```

If Keycloak starts but the federation provider cannot query users, the issue is usually one of:

- PostgreSQL is not listening on the configured port.
- `pg_hba.conf` does not allow Docker bridge connections.
- The SPI JDBC URL points to the wrong database.
- The `auth_users`, `auth_roles`, or `auth_user_roles` tables are missing.
- The database password in Keycloak User Federation settings is incorrect.
