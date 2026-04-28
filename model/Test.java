package model;

public class Test {
    private int id;
    private int teacherId;
    private String title;
    private String description;
    private int timeLimitMinutes;
    private int maxAttempts;

    public Test() {
    }

    public Test(int id, int teacherId, String title, String description, int timeLimitMinutes) {
        this(id, teacherId, title, description, timeLimitMinutes, 1);
    }

    public Test(int id, int teacherId, String title, String description, int timeLimitMinutes, int maxAttempts) {
        this.id = id;
        this.teacherId = teacherId;
        this.title = title;
        this.description = description;
        this.timeLimitMinutes = timeLimitMinutes;
        this.maxAttempts = maxAttempts;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(int teacherId) {
        this.teacherId = teacherId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public void setTimeLimitMinutes(int timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
}
