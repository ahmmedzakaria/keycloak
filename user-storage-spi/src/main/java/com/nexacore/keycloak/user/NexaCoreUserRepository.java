package com.nexacore.keycloak.user;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NexaCoreUserRepository {

    private static final String USER_SELECT = """
            SELECT u.id,
                   u.person_id,
                   u.username,
                   u.email,
                   COALESCE(u.email_verified, false) AS email_verified,
                   u.enabled,
                   u.password
              FROM auth_users u
            """;

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String kycJdbcUrl;
    private final String kycUsername;
    private final String kycPassword;

    public NexaCoreUserRepository(String jdbcUrl,
                                  String username,
                                  String password,
                                  String kycJdbcUrl,
                                  String kycUsername,
                                  String kycPassword) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.kycJdbcUrl = kycJdbcUrl;
        this.kycUsername = kycUsername;
        this.kycPassword = kycPassword;
    }

    public Optional<NexaCoreUserRecord> findById(long id) {
        return findOne(USER_SELECT + " WHERE u.id = ?", statement -> statement.setLong(1, id));
    }

    public Optional<NexaCoreUserRecord> findByUsername(String username) {
        return findOne(USER_SELECT + " WHERE LOWER(u.username) = LOWER(?)", statement -> statement.setString(1, username));
    }

    public Optional<NexaCoreUserRecord> findByEmail(String email) {
        return findOne(USER_SELECT + " WHERE LOWER(u.email) = LOWER(?)", statement -> statement.setString(1, email));
    }

    public List<NexaCoreUserRecord> findAll(Integer firstResult, Integer maxResults) {
        String sql = USER_SELECT + " ORDER BY u.username OFFSET ? LIMIT ?";
        return findMany(sql, statement -> {
            statement.setInt(1, firstResult == null ? 0 : firstResult);
            statement.setInt(2, maxResults == null ? 100 : maxResults);
        });
    }

    public List<NexaCoreUserRecord> search(String search, Integer firstResult, Integer maxResults) {
        String sql = USER_SELECT + """
                 WHERE LOWER(u.username) LIKE LOWER(?)
                    OR LOWER(u.email) LIKE LOWER(?)
                 ORDER BY u.username
                 OFFSET ? LIMIT ?
                """;
        String term = "%" + (search == null ? "" : search.trim()) + "%";
        return findMany(sql, statement -> {
            statement.setString(1, term);
            statement.setString(2, term);
            statement.setInt(3, firstResult == null ? 0 : firstResult);
            statement.setInt(4, maxResults == null ? 100 : maxResults);
        });
    }

    public int count() {
        return count("SELECT COUNT(*) FROM auth_users", statement -> {
        });
    }

    public int countSearch(String search) {
        String sql = """
                SELECT COUNT(*)
                  FROM auth_users u
                 WHERE LOWER(u.username) LIKE LOWER(?)
                    OR LOWER(u.email) LIKE LOWER(?)
                """;
        String term = "%" + (search == null ? "" : search.trim()) + "%";
        return count(sql, statement -> {
            statement.setString(1, term);
            statement.setString(2, term);
        });
    }

    private Optional<NexaCoreUserRecord> findOne(String sql, StatementBinder binder) {
        List<NexaCoreUserRecord> users = findMany(sql, binder);
        return users.stream().findFirst();
    }

    private List<NexaCoreUserRecord> findMany(String sql, StatementBinder binder) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<NexaCoreUserRecord> users = new ArrayList<>();
                while (resultSet.next()) {
                    users.add(mapUser(connection, resultSet));
                }
                return users;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query NexaCore users", e);
        }
    }

    private int count(String sql, StatementBinder binder) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count NexaCore users", e);
        }
    }

    private NexaCoreUserRecord mapUser(Connection connection, ResultSet resultSet) throws SQLException {
        long userId = resultSet.getLong("id");
        Long personId = nullableLong(resultSet, "person_id");
        PersonProfile personProfile = findPersonProfile(personId).orElse(PersonProfile.empty());
        return new NexaCoreUserRecord(
                userId,
                personId,
                resultSet.getString("username"),
                firstNonBlank(personProfile.email(), resultSet.getString("email")),
                personProfile.firstName(),
                personProfile.lastName(),
                personProfile.emailVerified() == null ? resultSet.getBoolean("email_verified") : personProfile.emailVerified(),
                resultSet.getBoolean("enabled"),
                resultSet.getString("password"),
                findRoles(connection, userId)
        );
    }

    private List<String> findRoles(Connection connection, long userId) throws SQLException {
        String sql = """
                SELECT r.name
                  FROM auth_roles r
                  JOIN auth_user_roles ur ON ur.role_id = r.id
                 WHERE ur.user_id = ?
                 ORDER BY r.name
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> roles = new ArrayList<>();
                while (resultSet.next()) {
                    roles.add(resultSet.getString("name"));
                }
                return roles;
            }
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private Optional<PersonProfile> findPersonProfile(Long personId) {
        if (personId == null || isBlank(kycJdbcUrl)) {
            return Optional.empty();
        }

        String sql = """
                SELECT p.email,
                       p.first_name,
                       p.last_name,
                       p.email_verified
                  FROM kyc_person p
                 WHERE p.id = ?
                """;
        try (Connection connection = DriverManager.getConnection(kycJdbcUrl, kycUsername, kycPassword);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, personId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PersonProfile(
                        resultSet.getString("email"),
                        resultSet.getString("first_name"),
                        resultSet.getString("last_name"),
                        resultSet.getBoolean("email_verified")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query NexaCore person profile", e);
        }
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record PersonProfile(String email, String firstName, String lastName, Boolean emailVerified) {
        private static PersonProfile empty() {
            return new PersonProfile(null, null, null, null);
        }
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
