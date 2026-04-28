import backend.QuestionService;
import backend.QuizService;
import backend.AuthService;
import backend.TestService;
import database.DatabaseInitializer;
import database.QuestionDAO;
import database.ResultDAO;
import database.TestAttemptDAO;
import database.TestDAO;
import database.UserDAO;
import frontend.WebQuizUI;

public class AppLauncher {
    public static void main(String[] args) {
        DatabaseInitializer.initialize();

        QuestionDAO questionDAO = new QuestionDAO();
        TestDAO testDAO = new TestDAO();
        TestAttemptDAO testAttemptDAO = new TestAttemptDAO();
        UserDAO userDAO = new UserDAO();
        ResultDAO resultDAO = new ResultDAO();
        QuestionService questionService = new QuestionService(questionDAO);
        QuizService quizService = new QuizService(questionService, resultDAO);
        TestService testService = new TestService(testDAO, questionDAO, resultDAO, testAttemptDAO);
        AuthService authService = new AuthService(userDAO);
        WebQuizUI quizUI = new WebQuizUI(quizService, testService, authService, 8080);

        quizUI.start();
    }
}
