package database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {
    private DatabaseInitializer() {
    }

    public static void initialize() {
        try {
            createDatabase();
            createTables();
            migrateTables();
            insertDefaultQuestions();
            System.out.println("MySQL database is ready.");
        } catch (SQLException exception) {
            System.out.println("Database setup skipped: " + exception.getMessage());
        }
    }

    private static void createDatabase() throws SQLException {
        String databaseName = DBConnection.getDatabaseName();

        if (!databaseName.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("Invalid database name: " + databaseName);
        }

        try (Connection connection = DBConnection.getServerConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS " + databaseName);
        }
    }

    private static void createTables() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "name VARCHAR(100) NOT NULL,"
                    + "username VARCHAR(100) NOT NULL UNIQUE,"
                    + "email VARCHAR(150) NOT NULL UNIQUE,"
                    + "password_hash VARCHAR(255) NOT NULL,"
                    + "role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',"
                    + "score INT NOT NULL DEFAULT 0,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS tests ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "teacher_id INT NOT NULL,"
                    + "title VARCHAR(150) NOT NULL,"
                    + "description TEXT,"
                    + "time_limit_minutes INT NOT NULL DEFAULT 10,"
                    + "max_attempts INT NOT NULL DEFAULT 1,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS questions ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "test_id INT NOT NULL DEFAULT 1,"
                    + "question TEXT NOT NULL,"
                    + "option_a VARCHAR(255) NOT NULL,"
                    + "option_b VARCHAR(255) NOT NULL,"
                    + "option_c VARCHAR(255) NOT NULL,"
                    + "option_d VARCHAR(255) NOT NULL,"
                    + "correct_answer CHAR(1) NOT NULL"
                    + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS quiz_results ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "user_id INT NOT NULL,"
                    + "score INT NOT NULL,"
                    + "total_questions INT NOT NULL,"
                    + "taken_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "CONSTRAINT fk_quiz_results_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS test_results ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "test_id INT NOT NULL,"
                    + "student_id INT NOT NULL,"
                    + "score INT NOT NULL,"
                    + "total_questions INT NOT NULL,"
                    + "taken_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS test_attempts ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "test_id INT NOT NULL,"
                    + "student_id INT NOT NULL,"
                    + "attempt_number INT NOT NULL DEFAULT 1,"
                    + "status VARCHAR(30) NOT NULL,"
                    + "start_time_ms BIGINT NOT NULL,"
                    + "deadline_ms BIGINT NOT NULL,"
                    + "submitted_time_ms BIGINT NOT NULL DEFAULT 0,"
                    + "score INT NOT NULL DEFAULT 0,"
                    + "total_questions INT NOT NULL DEFAULT 0,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "UNIQUE KEY uq_test_attempt_number (test_id, student_id, attempt_number)"
                    + ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS suspicious_activity ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "attempt_id INT NOT NULL,"
                    + "test_id INT NOT NULL,"
                    + "student_id INT NOT NULL,"
                    + "activity_type VARCHAR(80) NOT NULL,"
                    + "details VARCHAR(255),"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")");
        }
    }

    private static void migrateTables() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                Statement statement = connection.createStatement()) {
            executeIgnoringDuplicate(statement, "ALTER TABLE users ADD COLUMN username VARCHAR(100)");
            executeIgnoringDuplicate(statement, "ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'STUDENT'");
            statement.executeUpdate("UPDATE users SET username = LOWER(SUBSTRING_INDEX(email, '@', 1)) WHERE username IS NULL OR username = ''");
            executeIgnoringDuplicate(statement, "CREATE UNIQUE INDEX idx_users_username ON users(username)");
            executeIgnoringDuplicate(statement, "ALTER TABLE questions ADD COLUMN test_id INT NOT NULL DEFAULT 1");
            executeIgnoringDuplicate(statement, "ALTER TABLE tests ADD COLUMN max_attempts INT NOT NULL DEFAULT 1");
            executeIgnoringDuplicate(statement, "ALTER TABLE test_attempts ADD COLUMN attempt_number INT NOT NULL DEFAULT 1");
            executeIgnoringDuplicate(statement, "DROP INDEX uq_test_attempt_student ON test_attempts");
            executeIgnoringDuplicate(statement, "CREATE UNIQUE INDEX uq_test_attempt_number ON test_attempts(test_id, student_id, attempt_number)");
        }
    }

    private static void executeIgnoringDuplicate(Statement statement, String sql) throws SQLException {
        try {
            statement.executeUpdate(sql);
        } catch (SQLException exception) {
            String state = exception.getSQLState();
            int code = exception.getErrorCode();

            if (code != 1060 && code != 1061 && code != 1062 && code != 1091 && !"42S21".equals(state)
                    && !"42000".equals(state)) {
                throw exception;
            }
        }
    }

    private static void insertDefaultQuestions() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT IGNORE INTO tests "
                    + "(id, teacher_id, title, description, time_limit_minutes, max_attempts) VALUES "
                    + "(1, 1, 'Java OOP Basics', 'Sample test for students.', 10, 1)");
            statement.executeUpdate("INSERT IGNORE INTO questions "
                    + "(id, test_id, question, option_a, option_b, option_c, option_d, correct_answer) VALUES "
                    + "(1, 1, 'Which programming language is used for Android app development?', 'Java', 'Python', 'Ruby', 'Swift', 'A'),"
                    + "(2, 1, 'Which OOP concept hides internal object details?', 'Inheritance', 'Encapsulation', 'Polymorphism', 'Compilation', 'B'),"
                    + "(3, 1, 'Which keyword is used to inherit a class in Java?', 'implements', 'inherits', 'extends', 'super', 'C'),"
                    + "(4, 1, 'Which method is the entry point of a Java application?', 'start()', 'main()', 'run()', 'init()', 'B'),"
                    + "(5, 1, 'Which package contains the Scanner class?', 'java.io', 'java.sql', 'java.util', 'java.lang', 'C')");
        }
    }
}
