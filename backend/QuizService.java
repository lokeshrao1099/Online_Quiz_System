package backend;

import java.sql.SQLException;
import java.util.List;

import database.ResultDAO;
import model.Question;
import model.User;

public class QuizService {
    private final QuestionService questionService;
    private final ResultDAO resultDAO;

    public QuizService(QuestionService questionService) {
        this(questionService, null);
    }

    public QuizService(QuestionService questionService, ResultDAO resultDAO) {
        this.questionService = questionService;
        this.resultDAO = resultDAO;
    }

    public List<Question> startQuiz() {
        return questionService.getQuestions();
    }

    public boolean isValidAnswer(String answer) {
        if (answer == null) {
            return false;
        }

        return answer.equalsIgnoreCase("A")
                || answer.equalsIgnoreCase("B")
                || answer.equalsIgnoreCase("C")
                || answer.equalsIgnoreCase("D");
    }

    public boolean isCorrectAnswer(Question question, String userAnswer) {
        if (question == null || userAnswer == null) {
            return false;
        }

        return question.getCorrectAnswer().equalsIgnoreCase(userAnswer.trim());
    }

    public int calculateScore(List<Question> questions, List<String> userAnswers) {
        int score = 0;

        for (int index = 0; index < questions.size(); index++) {
            if (isCorrectAnswer(questions.get(index), userAnswers.get(index))) {
                score++;
            }
        }

        return score;
    }

    public void updateUserScore(User user, int score) {
        user.setScore(score);
    }

    public boolean saveQuizResult(User user, int score, int totalQuestions) {
        updateUserScore(user, score);

        if (resultDAO == null || user == null || user.getId() <= 0) {
            return false;
        }

        try {
            resultDAO.saveResult(user, score, totalQuestions);
            return true;
        } catch (SQLException exception) {
            System.out.println("Could not save quiz result: " + exception.getMessage());
            return false;
        }
    }
}
