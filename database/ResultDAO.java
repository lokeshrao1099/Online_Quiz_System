package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import model.TestResult;
import model.User;

public class ResultDAO {
    private static final String INSERT_RESULT = "INSERT INTO quiz_results (user_id, score, total_questions) VALUES (?, ?, ?)";
    private static final String UPDATE_USER_SCORE = "UPDATE users SET score = ? WHERE id = ?";
    private static final String INSERT_TEST_RESULT = "INSERT INTO test_results (test_id, student_id, score, total_questions) VALUES (?, ?, ?, ?)";

    public void saveResult(User user, int score, int totalQuestions) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement resultStatement = connection.prepareStatement(INSERT_RESULT);
                    PreparedStatement userStatement = connection.prepareStatement(UPDATE_USER_SCORE)) {
                resultStatement.setInt(1, user.getId());
                resultStatement.setInt(2, score);
                resultStatement.setInt(3, totalQuestions);
                resultStatement.executeUpdate();

                userStatement.setInt(1, score);
                userStatement.setInt(2, user.getId());
                userStatement.executeUpdate();

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void saveTestResult(int testId, User student, int score, int totalQuestions) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement resultStatement = connection.prepareStatement(INSERT_TEST_RESULT);
                    PreparedStatement userStatement = connection.prepareStatement(UPDATE_USER_SCORE)) {
                resultStatement.setInt(1, testId);
                resultStatement.setInt(2, student.getId());
                resultStatement.setInt(3, score);
                resultStatement.setInt(4, totalQuestions);
                resultStatement.executeUpdate();

                userStatement.setInt(1, score);
                userStatement.setInt(2, student.getId());
                userStatement.executeUpdate();

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<TestResult> getResultsForTeacher(int teacherId) throws SQLException {
        String sql = "SELECT tr.id, tr.test_id, t.title, tr.student_id, u.name, u.username, "
                + "tr.score, tr.total_questions, tr.taken_at "
                + "FROM test_results tr "
                + "JOIN tests t ON tr.test_id = t.id "
                + "JOIN users u ON tr.student_id = u.id "
                + "WHERE t.teacher_id = ? "
                + "ORDER BY tr.taken_at DESC";
        List<TestResult> results = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, teacherId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(new TestResult(
                            resultSet.getInt("id"),
                            resultSet.getInt("test_id"),
                            resultSet.getString("title"),
                            resultSet.getInt("student_id"),
                            resultSet.getString("name"),
                            resultSet.getString("username"),
                            resultSet.getInt("score"),
                            resultSet.getInt("total_questions"),
                            resultSet.getString("taken_at")));
                }
            }
        }

        return results;
    }
}
