package backend;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import database.QuestionDAO;
import database.ResultDAO;
import database.TestAttemptDAO;
import database.TestDAO;
import model.AttemptStartResult;
import model.Question;
import model.SuspiciousActivity;
import model.Test;
import model.TestAttempt;
import model.TestResult;
import model.User;

public class TestService {
    private final TestDAO testDAO;
    private final QuestionDAO questionDAO;
    private final ResultDAO resultDAO;
    private final TestAttemptDAO testAttemptDAO;

    public TestService(TestDAO testDAO, QuestionDAO questionDAO, ResultDAO resultDAO, TestAttemptDAO testAttemptDAO) {
        this.testDAO = testDAO;
        this.questionDAO = questionDAO;
        this.resultDAO = resultDAO;
        this.testAttemptDAO = testAttemptDAO;
    }

    public int createTest(User teacher, String title, String description, int timeLimitMinutes, int maxAttempts)
            throws SQLException {
        if (teacher == null || !teacher.isTeacher()) {
            throw new IllegalArgumentException("Only teachers can create tests.");
        }

        Test test = new Test();
        test.setTeacherId(teacher.getId());
        test.setTitle(clean(title));
        test.setDescription(clean(description));
        test.setTimeLimitMinutes(Math.max(1, timeLimitMinutes));
        test.setMaxAttempts(Math.max(1, maxAttempts));
        return testDAO.createTest(test);
    }

    public void addQuestion(int testId, String questionText, String optionA, String optionB, String optionC,
            String optionD, String correctAnswer) throws SQLException {
        Question question = new Question();
        question.setTestId(testId);
        question.setQuestion(clean(questionText));
        question.setOptionA(clean(optionA));
        question.setOptionB(clean(optionB));
        question.setOptionC(clean(optionC));
        question.setOptionD(clean(optionD));
        question.setCorrectAnswer(clean(correctAnswer).toUpperCase());
        questionDAO.createQuestion(question);
    }

    public List<Test> getAllTests() {
        try {
            return testDAO.getAllTests();
        } catch (SQLException exception) {
            System.out.println("Could not load tests: " + exception.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Test> getTestsByTeacher(int teacherId) {
        try {
            return testDAO.getTestsByTeacher(teacherId);
        } catch (SQLException exception) {
            System.out.println("Could not load teacher tests: " + exception.getMessage());
            return new ArrayList<>();
        }
    }

    public Test findTest(int testId) {
        try {
            return testDAO.findById(testId);
        } catch (SQLException exception) {
            System.out.println("Could not load test: " + exception.getMessage());
            return null;
        }
    }

    public List<Question> getQuestionsForTest(int testId) {
        try {
            return questionDAO.getQuestionsByTestId(testId);
        } catch (SQLException exception) {
            System.out.println("Could not load questions: " + exception.getMessage());
            return new ArrayList<>();
        }
    }

    public int calculateScore(List<Question> questions, Map<Integer, String> answers) {
        int score = 0;

        for (Question question : questions) {
            String answer = answers.get(question.getId());

            if (answer != null && question.getCorrectAnswer().equalsIgnoreCase(answer.trim())) {
                score++;
            }
        }

        return score;
    }

    public boolean saveStudentResult(int testId, User student, int score, int totalQuestions) {
        if (student == null || !student.isStudent()) {
            return false;
        }

        try {
            resultDAO.saveTestResult(testId, student, score, totalQuestions);
            student.setScore(score);
            return true;
        } catch (SQLException exception) {
            System.out.println("Could not save test result: " + exception.getMessage());
            return false;
        }
    }

    public AttemptStartResult startOrResumeAttempt(User student, Test test, int totalQuestions) {
        if (student == null || !student.isStudent()) {
            return AttemptStartResult.blocked("Only students can attempt tests.", null);
        }

        try {
            TestAttempt attempt = testAttemptDAO.findActiveByStudentAndTest(student.getId(), test.getId());

            if (attempt != null && System.currentTimeMillis() > attempt.getDeadlineMs()) {
                autoSubmitExpiredAttempt(attempt, student, totalQuestions);
                attempt = null;
            }

            if (attempt != null) {
                return AttemptStartResult.allowed(attempt);
            }

            int usedAttempts = testAttemptDAO.countAttempts(student.getId(), test.getId());

            if (usedAttempts >= test.getMaxAttempts()) {
                return AttemptStartResult.blocked("Test already attempted. Maximum attempts used.", null);
            }

            long now = System.currentTimeMillis();
            long deadline = now + (test.getTimeLimitMinutes() * 60_000L);
            return AttemptStartResult.allowed(
                    testAttemptDAO.createAttempt(test.getId(), student.getId(), usedAttempts + 1, now, deadline));
        } catch (SQLException exception) {
            return AttemptStartResult.blocked("Could not start attempt: " + exception.getMessage(), null);
        }
    }

    public TestAttempt findAttempt(int attemptId) {
        try {
            return testAttemptDAO.findById(attemptId);
        } catch (SQLException exception) {
            System.out.println("Could not load attempt: " + exception.getMessage());
            return null;
        }
    }

    public boolean submitAttempt(TestAttempt attempt, User student, int score, int totalQuestions, boolean forceAutoSubmit) {
        if (attempt == null || student == null || attempt.getStudentId() != student.getId() || attempt.isFinished()) {
            return false;
        }

        try {
            boolean expired = System.currentTimeMillis() > attempt.getDeadlineMs();
            String status = forceAutoSubmit || expired ? "AUTO_SUBMITTED" : "SUBMITTED";
            testAttemptDAO.markSubmitted(attempt.getId(), status, score, totalQuestions, System.currentTimeMillis());
            resultDAO.saveTestResult(attempt.getTestId(), student, score, totalQuestions);
            student.setScore(score);
            return true;
        } catch (SQLException exception) {
            System.out.println("Could not submit attempt: " + exception.getMessage());
            return false;
        }
    }

    public void autoSubmitExpiredAttempt(TestAttempt attempt, User student, int totalQuestions) {
        if (attempt == null || attempt.isFinished()) {
            return;
        }

        try {
            testAttemptDAO.markSubmitted(attempt.getId(), "AUTO_SUBMITTED", 0, totalQuestions, System.currentTimeMillis());
            if (student != null) {
                resultDAO.saveTestResult(attempt.getTestId(), student, 0, totalQuestions);
                student.setScore(0);
            }
        } catch (SQLException exception) {
            System.out.println("Could not auto-submit expired attempt: " + exception.getMessage());
        }
    }

    public boolean cancelAttempt(TestAttempt attempt, User student, String details) {
        if (attempt == null || student == null || attempt.getStudentId() != student.getId() || attempt.isFinished()) {
            return false;
        }

        try {
            testAttemptDAO.logSuspiciousActivity(attempt.getId(), attempt.getTestId(), student.getId(),
                    "FULLSCREEN_EXIT_CANCEL", clean(details));
            testAttemptDAO.markSubmitted(attempt.getId(), "CANCELED", 0, 0, System.currentTimeMillis());
            return true;
        } catch (SQLException exception) {
            System.out.println("Could not cancel attempt: " + exception.getMessage());
            return false;
        }
    }

    public void logSuspiciousActivity(int attemptId, int testId, User student, String activityType, String details) {
        if (student == null || attemptId <= 0 || testId <= 0) {
            return;
        }

        try {
            testAttemptDAO.logSuspiciousActivity(attemptId, testId, student.getId(), clean(activityType), clean(details));
        } catch (SQLException exception) {
            System.out.println("Could not log suspicious activity: " + exception.getMessage());
        }
    }

    public void logActiveAttemptLogin(User student) {
        if (student == null || !student.isStudent()) {
            return;
        }

        try {
            for (TestAttempt attempt : testAttemptDAO.findActiveAttemptsByStudent(student.getId())) {
                if (System.currentTimeMillis() > attempt.getDeadlineMs()) {
                    autoSubmitExpiredAttempt(attempt, student, 0);
                } else {
                    testAttemptDAO.logSuspiciousActivity(attempt.getId(), attempt.getTestId(), student.getId(),
                            "LOGIN_DURING_ACTIVE_TEST", "Student logged in while a test attempt was active.");
                }
            }
        } catch (SQLException exception) {
            System.out.println("Could not check active attempts: " + exception.getMessage());
        }
    }

    public List<TestResult> getResultsForTeacher(int teacherId) {
        try {
            return resultDAO.getResultsForTeacher(teacherId);
        } catch (SQLException exception) {
            System.out.println("Could not load results: " + exception.getMessage());
            return new ArrayList<>();
        }
    }

    public List<SuspiciousActivity> getSecurityLogsForTeacher(int teacherId) {
        try {
            return testAttemptDAO.getSecurityLogsForTeacher(teacherId);
        } catch (SQLException exception) {
            System.out.println("Could not load security logs: " + exception.getMessage());
            return new ArrayList<>();
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
