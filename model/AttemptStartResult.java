package model;

public class AttemptStartResult {
    private final boolean allowed;
    private final String message;
    private final TestAttempt attempt;

    private AttemptStartResult(boolean allowed, String message, TestAttempt attempt) {
        this.allowed = allowed;
        this.message = message;
        this.attempt = attempt;
    }

    public static AttemptStartResult allowed(TestAttempt attempt) {
        return new AttemptStartResult(true, "", attempt);
    }

    public static AttemptStartResult blocked(String message, TestAttempt attempt) {
        return new AttemptStartResult(false, message, attempt);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getMessage() {
        return message;
    }

    public TestAttempt getAttempt() {
        return attempt;
    }
}
