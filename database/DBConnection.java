package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String DEFAULT_SERVER_URL = "jdbc:mysql://localhost:3306/";
    private static final String DEFAULT_DATABASE_NAME = "quiz_app";
    private static final String DEFAULT_URL = DEFAULT_SERVER_URL + DEFAULT_DATABASE_NAME;
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "Lokesh@321";

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        loadMySqlDriver();
        return DriverManager.getConnection(getDatabaseUrl(), getUsername(), getPassword());
    }

    public static Connection getServerConnection() throws SQLException {
        loadMySqlDriver();
        return DriverManager.getConnection(getServerUrl(), getUsername(), getPassword());
    }

    public static String getDatabaseUrl() {
        return getConfigValue("quiz.db.url", "QUIZ_DB_URL", DEFAULT_URL);
    }

    public static String getServerUrl() {
        return getConfigValue("quiz.db.serverUrl", "QUIZ_DB_SERVER_URL", DEFAULT_SERVER_URL);
    }

    public static String getDatabaseName() {
        return getConfigValue("quiz.db.name", "QUIZ_DB_NAME", DEFAULT_DATABASE_NAME);
    }

    public static String getUsername() {
        return getConfigValue("quiz.db.user", "QUIZ_DB_USER", DEFAULT_USERNAME);
    }

    public static String getPassword() {
        return getConfigValue("quiz.db.password", "QUIZ_DB_PASSWORD", DEFAULT_PASSWORD);
    }

    private static void loadMySqlDriver() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException fallbackException) {
                throw new SQLException("MySQL JDBC driver not found. Add mysql-connector-j to the classpath.",
                        fallbackException);
            }
        }
    }

    private static String getConfigValue(String propertyName, String environmentName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);

        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue.trim();
        }

        String environmentValue = System.getenv(environmentName);

        if (environmentValue != null && !environmentValue.trim().isEmpty()) {
            return environmentValue.trim();
        }

        return defaultValue;
    }
}
