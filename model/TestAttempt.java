package model;

public class TestAttempt {
    private int id;
    private int testId;
    private int studentId;
    private int attemptNumber;
    private String status;
    private long startTimeMs;
    private long deadlineMs;
    private long submittedTimeMs;
    private int score;
    private int totalQuestions;

    public TestAttempt(int id, int testId, int studentId, int attemptNumber, String status, long startTimeMs, long deadlineMs,
            long submittedTimeMs, int score, int totalQuestions) {
        this.id = id;
        this.testId = testId;
        this.studentId = studentId;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.startTimeMs = startTimeMs;
        this.deadlineMs = deadlineMs;
        this.submittedTimeMs = submittedTimeMs;
        this.score = score;
        this.totalQuestions = totalQuestions;
    }

    public int getId() {
        return id;
    }

    public int getTestId() {
        return testId;
    }

    public int getStudentId() {
        return studentId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getStatus() {
        return status;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getDeadlineMs() {
        return deadlineMs;
    }

    public long getSubmittedTimeMs() {
        return submittedTimeMs;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public boolean isSubmitted() {
        return "SUBMITTED".equalsIgnoreCase(status) || "AUTO_SUBMITTED".equalsIgnoreCase(status);
    }

    public boolean isFinished() {
        return isSubmitted() || "CANCELED".equalsIgnoreCase(status);
    }

    public boolean isInProgress() {
        return "IN_PROGRESS".equalsIgnoreCase(status);
    }
}
