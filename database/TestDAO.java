package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import model.Test;

public class TestDAO {
    public int createTest(Test test) throws SQLException {
        String sql = "INSERT INTO tests (teacher_id, title, description, time_limit_minutes, max_attempts) VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, test.getTeacherId());
            statement.setString(2, test.getTitle());
            statement.setString(3, test.getDescription());
            statement.setInt(4, test.getTimeLimitMinutes());
            statement.setInt(5, test.getMaxAttempts());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        return 0;
    }

    public List<Test> getAllTests() throws SQLException {
        String sql = "SELECT id, teacher_id, title, description, time_limit_minutes, max_attempts FROM tests ORDER BY id DESC";
        List<Test> tests = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tests.add(mapTest(resultSet));
            }
        }

        return tests;
    }

    public List<Test> getTestsByTeacher(int teacherId) throws SQLException {
        String sql = "SELECT id, teacher_id, title, description, time_limit_minutes, max_attempts FROM tests WHERE teacher_id = ? ORDER BY id DESC";
        List<Test> tests = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, teacherId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tests.add(mapTest(resultSet));
                }
            }
        }

        return tests;
    }

    public Test findById(int testId) throws SQLException {
        String sql = "SELECT id, teacher_id, title, description, time_limit_minutes, max_attempts FROM tests WHERE id = ?";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, testId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapTest(resultSet);
                }
            }
        }

        return null;
    }

    private Test mapTest(ResultSet resultSet) throws SQLException {
        return new Test(
                resultSet.getInt("id"),
                resultSet.getInt("teacher_id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getInt("time_limit_minutes"),
                resultSet.getInt("max_attempts"));
    }
}
