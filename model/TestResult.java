package model;

public class TestResult {
    private int id;
    private int testId;
    private String testTitle;
    private int studentId;
    private String studentName;
    private String studentUsername;
    private int score;
    private int totalQuestions;
    private String takenAt;

    public TestResult(int id, int testId, String testTitle, int studentId, String studentName, String studentUsername,
            int score, int totalQuestions, String takenAt) {
        this.id = id;
        this.testId = testId;
        this.testTitle = testTitle;
        this.studentId = studentId;
        this.studentName = studentName;
        this.studentUsername = studentUsername;
        this.score = score;
        this.totalQuestions = totalQuestions;
        this.takenAt = takenAt;
    }

    public int getId() {
        return id;
    }

    public int getTestId() {
        return testId;
    }

    public String getTestTitle() {
        return testTitle;
    }

    public int getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getStudentUsername() {
        return studentUsername;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public String getTakenAt() {
        return takenAt;
    }
}
