package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import model.Question;

public class QuestionDAO implements QuestionRepository {
    private static final String SELECT_ALL_QUESTIONS = "SELECT id, test_id, question, option_a, option_b, option_c, option_d, correct_answer FROM questions";
    private static final String SELECT_BY_TEST = SELECT_ALL_QUESTIONS + " WHERE test_id = ? ORDER BY id";
    private static final String INSERT_QUESTION = "INSERT INTO questions (test_id, question, option_a, option_b, option_c, option_d, correct_answer) VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public List<Question> getAllQuestions() {
        List<Question> questions = fetchQuestionsFromDatabase();

        if (questions.isEmpty()) {
            System.out.println("Using sample questions because the database is unavailable or empty.");
            questions = getDummyQuestions();
        }

        return questions;
    }

    private List<Question> fetchQuestionsFromDatabase() {
        List<Question> questions = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(SELECT_ALL_QUESTIONS)) {

            while (resultSet.next()) {
                questions.add(mapQuestion(resultSet));
            }
        } catch (SQLException exception) {
            System.out.println("Database error: " + exception.getMessage());
        }

        return questions;
    }

    public List<Question> getQuestionsByTestId(int testId) throws SQLException {
        List<Question> questions = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_BY_TEST)) {
            statement.setInt(1, testId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    questions.add(mapQuestion(resultSet));
                }
            }
        }

        return questions;
    }

    public void createQuestion(Question question) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_QUESTION)) {
            statement.setInt(1, question.getTestId());
            statement.setString(2, question.getQuestion());
            statement.setString(3, question.getOptionA());
            statement.setString(4, question.getOptionB());
            statement.setString(5, question.getOptionC());
            statement.setString(6, question.getOptionD());
            statement.setString(7, question.getCorrectAnswer());
            statement.executeUpdate();
        }
    }

    private Question mapQuestion(ResultSet resultSet) throws SQLException {
        return new Question(
                resultSet.getInt("id"),
                resultSet.getInt("test_id"),
                resultSet.getString("question"),
                resultSet.getString("option_a"),
                resultSet.getString("option_b"),
                resultSet.getString("option_c"),
                resultSet.getString("option_d"),
                resultSet.getString("correct_answer"));
    }

    private List<Question> getDummyQuestions() {
        List<Question> questions = new ArrayList<>();

        questions.add(new Question(1, "Which programming language is used for Android app development?",
                "Java", "Python", "Ruby", "Swift", "A"));
        questions.add(new Question(2, "Which OOP concept hides internal object details?",
                "Inheritance", "Encapsulation", "Polymorphism", "Compilation", "B"));
        questions.add(new Question(3, "Which keyword is used to inherit a class in Java?",
                "implements", "inherits", "extends", "super", "C"));
        questions.add(new Question(4, "Which method is the entry point of a Java application?",
                "start()", "main()", "run()", "init()", "B"));
        questions.add(new Question(5, "Which package contains the Scanner class?",
                "java.io", "java.sql", "java.util", "java.lang", "C"));

        return questions;
    }
}
