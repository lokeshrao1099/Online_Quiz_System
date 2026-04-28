package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import model.SuspiciousActivity;
import model.TestAttempt;

public class TestAttemptDAO {
    public TestAttempt createAttempt(int testId, int studentId, int attemptNumber, long startTimeMs, long deadlineMs)
            throws SQLException {
        String sql = "INSERT INTO test_attempts (test_id, student_id, attempt_number, status, start_time_ms, deadline_ms) VALUES (?, ?, ?, 'IN_PROGRESS', ?, ?)";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, testId);
            statement.setInt(2, studentId);
            statement.setInt(3, attemptNumber);
            statement.setLong(4, startTimeMs);
            statement.setLong(5, deadlineMs);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getInt(1));
                }
            }
        }

        return findActiveByStudentAndTest(studentId, testId);
    }

    public TestAttempt findActiveByStudentAndTest(int studentId, int testId) throws SQLException {
        String sql = "SELECT id, test_id, student_id, attempt_number, status, start_time_ms, deadline_ms, submitted_time_ms, score, total_questions "
                + "FROM test_attempts WHERE student_id = ? AND test_id = ? AND status = 'IN_PROGRESS' ORDER BY id DESC LIMIT 1";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            statement.setInt(2, testId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapAttempt(resultSet);
                }
            }
        }

        return null;
    }

    public int countAttempts(int studentId, int testId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM test_attempts WHERE student_id = ? AND test_id = ?";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);
            statement.setInt(2, testId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }

        return 0;
    }

    public TestAttempt findById(int attemptId) throws SQLException {
        String sql = "SELECT id, test_id, student_id, attempt_number, status, start_time_ms, deadline_ms, submitted_time_ms, score, total_questions "
                + "FROM test_attempts WHERE id = ?";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, attemptId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapAttempt(resultSet);
                }
            }
        }

        return null;
    }

    public List<TestAttempt> findActiveAttemptsByStudent(int studentId) throws SQLException {
        String sql = "SELECT id, test_id, student_id, attempt_number, status, start_time_ms, deadline_ms, submitted_time_ms, score, total_questions "
                + "FROM test_attempts WHERE student_id = ? AND status = 'IN_PROGRESS'";
        List<TestAttempt> attempts = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, studentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    attempts.add(mapAttempt(resultSet));
                }
            }
        }

        return attempts;
    }

    public void markSubmitted(int attemptId, String status, int score, int totalQuestions, long submittedTimeMs)
            throws SQLException {
        String sql = "UPDATE test_attempts SET status = ?, score = ?, total_questions = ?, submitted_time_ms = ? WHERE id = ? AND status = 'IN_PROGRESS'";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, score);
            statement.setInt(3, totalQuestions);
            statement.setLong(4, submittedTimeMs);
            statement.setInt(5, attemptId);
            statement.executeUpdate();
        }
    }

    public void logSuspiciousActivity(int attemptId, int testId, int studentId, String activityType, String details)
            throws SQLException {
        String sql = "INSERT INTO suspicious_activity (attempt_id, test_id, student_id, activity_type, details) VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, attemptId);
            statement.setInt(2, testId);
            statement.setInt(3, studentId);
            statement.setString(4, activityType);
            statement.setString(5, details);
            statement.executeUpdate();
        }
    }

    public List<SuspiciousActivity> getSecurityLogsForTeacher(int teacherId) throws SQLException {
        String sql = "SELECT sa.id, sa.attempt_id, t.title, u.name, u.username, sa.activity_type, sa.details, sa.created_at "
                + "FROM suspicious_activity sa "
                + "JOIN tests t ON sa.test_id = t.id "
                + "JOIN users u ON sa.student_id = u.id "
                + "WHERE t.teacher_id = ? "
                + "ORDER BY sa.created_at DESC";
        List<SuspiciousActivity> logs = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, teacherId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    logs.add(new SuspiciousActivity(
                            resultSet.getInt("id"),
                            resultSet.getInt("attempt_id"),
                            resultSet.getString("title"),
                            resultSet.getString("name"),
                            resultSet.getString("username"),
                            resultSet.getString("activity_type"),
                            resultSet.getString("details"),
                            resultSet.getString("created_at")));
                }
            }
        }

        return logs;
    }

    private TestAttempt mapAttempt(ResultSet resultSet) throws SQLException {
        return new TestAttempt(
                resultSet.getInt("id"),
                resultSet.getInt("test_id"),
                resultSet.getInt("student_id"),
                resultSet.getInt("attempt_number"),
                resultSet.getString("status"),
                resultSet.getLong("start_time_ms"),
                resultSet.getLong("deadline_ms"),
                resultSet.getLong("submitted_time_ms"),
                resultSet.getInt("score"),
                resultSet.getInt("total_questions"));
    }
}
