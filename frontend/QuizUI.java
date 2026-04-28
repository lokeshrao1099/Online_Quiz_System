package frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import backend.QuizService;
import model.Question;
import model.User;

public class QuizUI {
    private final QuizService quizService;
    private final Scanner scanner;

    public QuizUI(QuizService quizService) {
        this.quizService = quizService;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        showWelcomeMessage();

        User user = createUser();
        List<Question> questions = quizService.startQuiz();

        if (questions.isEmpty()) {
            System.out.println("No questions are available right now.");
            return;
        }

        List<String> userAnswers = askQuestions(questions);
        int score = quizService.calculateScore(questions, userAnswers);
        quizService.updateUserScore(user, score);

        showResult(user, questions.size());
    }

    private void showWelcomeMessage() {
        System.out.println("====================================");
        System.out.println("        Java OOP Quiz Application    ");
        System.out.println("====================================");
    }

    private User createUser() {
        System.out.print("Enter your name: ");
        String name = scanner.nextLine().trim();

        if (name.isEmpty()) {
            name = "Guest";
        }

        return new User(name);
    }

    private List<String> askQuestions(List<Question> questions) {
        List<String> userAnswers = new ArrayList<>();

        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index);
            displayQuestion(index + 1, question);
            String answer = readValidAnswer();
            userAnswers.add(answer);
        }

        return userAnswers;
    }

    private void displayQuestion(int questionNumber, Question question) {
        System.out.println();
        System.out.println("Question " + questionNumber + ": " + question.getQuestion());
        System.out.println("A. " + question.getOptionA());
        System.out.println("B. " + question.getOptionB());
        System.out.println("C. " + question.getOptionC());
        System.out.println("D. " + question.getOptionD());
    }

    private String readValidAnswer() {
        while (true) {
            System.out.print("Your answer (A/B/C/D): ");
            String answer = scanner.nextLine().trim();

            if (quizService.isValidAnswer(answer)) {
                return answer.toUpperCase();
            }

            System.out.println("Invalid answer. Please enter A, B, C, or D.");
        }
    }

    private void showResult(User user, int totalQuestions) {
        System.out.println();
        System.out.println("====================================");
        System.out.println("Quiz Completed!");
        System.out.println("Name: " + user.getName());
        System.out.println("Score: " + user.getScore() + " out of " + totalQuestions);
        System.out.println("Percentage: " + calculatePercentage(user.getScore(), totalQuestions) + "%");
        System.out.println("====================================");
    }

    private int calculatePercentage(int score, int totalQuestions) {
        return (int) Math.round((score * 100.0) / totalQuestions);
    }
}
