package model;

public class SuspiciousActivity {
    private int id;
    private int attemptId;
    private String testTitle;
    private String studentName;
    private String studentUsername;
    private String activityType;
    private String details;
    private String createdAt;

    public SuspiciousActivity(int id, int attemptId, String testTitle, String studentName, String studentUsername,
            String activityType, String details, String createdAt) {
        this.id = id;
        this.attemptId = attemptId;
        this.testTitle = testTitle;
        this.studentName = studentName;
        this.studentUsername = studentUsername;
        this.activityType = activityType;
        this.details = details;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getAttemptId() {
        return attemptId;
    }

    public String getTestTitle() {
        return testTitle;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getStudentUsername() {
        return studentUsername;
    }

    public String getActivityType() {
        return activityType;
    }

    public String getDetails() {
        return details;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
