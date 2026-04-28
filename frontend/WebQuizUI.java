package frontend;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import backend.AuthService;
import backend.QuizService;
import backend.TestService;
import model.AuthResult;
import model.AttemptStartResult;
import model.Question;
import model.SuspiciousActivity;
import model.Test;
import model.TestAttempt;
import model.TestResult;
import model.User;

public class WebQuizUI {
    private static final String SESSION_COOKIE = "QUIZ_SESSION";

    private final QuizService quizService;
    private final TestService testService;
    private final AuthService authService;
    private final int port;
    private final Map<String, User> sessions;

    public WebQuizUI(QuizService quizService, TestService testService, AuthService authService, int port) {
        this.quizService = quizService;
        this.testService = testService;
        this.authService = authService;
        this.port = port;
        this.sessions = new ConcurrentHashMap<>();
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handleHome);
            server.createContext("/login", this::handleLogin);
            server.createContext("/register", this::handleRegister);
            server.createContext("/logout", this::handleLogout);
            server.createContext("/teacher", this::handleTeacherDashboard);
            server.createContext("/teacher/create", this::handleCreateTest);
            server.createContext("/teacher/question", this::handleQuestion);
            server.createContext("/student", this::handleStudentDashboard);
            server.createContext("/student/test", this::handleStudentTest);
            server.createContext("/student/submit", this::handleStudentSubmit);
            server.createContext("/student/activity", this::handleStudentActivity);
            server.createContext("/student/cancel", this::handleStudentCancel);
            server.setExecutor(null);
            server.start();

            System.out.println("Quiz Application is running on http://localhost:" + port);
            System.out.println("Press Ctrl + C in this terminal to stop the server.");
        } catch (IOException exception) {
            System.out.println("Unable to start server: " + exception.getMessage());
        }
    }

    private void handleHome(HttpExchange exchange) throws IOException {
        User user = getAuthenticatedUser(exchange);

        if (user == null) {
            redirect(exchange, "/login");
            return;
        }

        redirect(exchange, dashboardPath(user));
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            User user = getAuthenticatedUser(exchange);
            sendOrRedirectAuthPage(exchange, user, "login", "");
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }

        Map<String, String> formData = parseFormData(exchange);
        AuthResult result = authService.login(formData.get("username"), formData.get("password"));

        if (result.isSuccess()) {
            testService.logActiveAttemptLogin(result.getUser());
            createSession(exchange, result.getUser());
            redirect(exchange, dashboardPath(result.getUser()));
            return;
        }

        sendHtml(exchange, buildAuthPage("login", result.getMessage()));
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            User user = getAuthenticatedUser(exchange);
            sendOrRedirectAuthPage(exchange, user, "register", "");
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }

        Map<String, String> formData = parseFormData(exchange);
        AuthResult result = authService.register(formData.get("name"), formData.get("username"),
                formData.get("password"), formData.get("role"));

        if (result.isSuccess()) {
            createSession(exchange, result.getUser());
            redirect(exchange, dashboardPath(result.getUser()));
            return;
        }

        sendHtml(exchange, buildAuthPage("register", result.getMessage()));
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String sessionId = getCookieValue(exchange, SESSION_COOKIE);

        if (sessionId != null) {
            sessions.remove(sessionId);
        }

        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/; Max-Age=0; HttpOnly");
        redirect(exchange, "/login");
    }

    private void handleTeacherDashboard(HttpExchange exchange) throws IOException {
        User teacher = requireRole(exchange, "TEACHER");

        if (teacher == null) {
            return;
        }

        List<Test> tests = testService.getTestsByTeacher(teacher.getId());
        List<TestResult> results = testService.getResultsForTeacher(teacher.getId());
        List<SuspiciousActivity> securityLogs = testService.getSecurityLogsForTeacher(teacher.getId());
        sendHtml(exchange, buildTeacherDashboard(teacher, tests, results, securityLogs, ""));
    }

    private void handleCreateTest(HttpExchange exchange) throws IOException {
        User teacher = requireRole(exchange, "TEACHER");

        if (teacher == null) {
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendHtml(exchange, buildCreateTestPage(""));
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }

        Map<String, String> formData = parseFormData(exchange);

        try {
            int testId = testService.createTest(teacher, formData.get("title"), formData.get("description"),
                    parseInt(formData.get("timeLimit"), 10), parseInt(formData.get("maxAttempts"), 1));
            redirect(exchange, "/teacher/question?testId=" + testId);
        } catch (Exception exception) {
            sendHtml(exchange, buildCreateTestPage("Could not create test: " + exception.getMessage()));
        }
    }

    private void handleQuestion(HttpExchange exchange) throws IOException {
        User teacher = requireRole(exchange, "TEACHER");

        if (teacher == null) {
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI());
        int testId = parseInt(query.get("testId"), 0);
        Test test = testService.findTest(testId);

        if (test == null || test.getTeacherId() != teacher.getId()) {
            sendHtml(exchange, buildMessagePage("Test not found", "This test does not belong to your account.", "/teacher"));
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendHtml(exchange, buildQuestionPage(test, ""));
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }

        Map<String, String> formData = parseFormData(exchange);

        try {
            testService.addQuestion(testId, formData.get("question"), formData.get("optionA"), formData.get("optionB"),
                    formData.get("optionC"), formData.get("optionD"), formData.get("correctAnswer"));
            sendHtml(exchange, buildQuestionPage(test, "Question added successfully."));
        } catch (Exception exception) {
            sendHtml(exchange, buildQuestionPage(test, "Could not add question: " + exception.getMessage()));
        }
    }

    private void handleStudentDashboard(HttpExchange exchange) throws IOException {
        User student = requireRole(exchange, "STUDENT");

        if (student == null) {
            return;
        }

        sendHtml(exchange, buildStudentDashboard(student, testService.getAllTests()));
    }

    private void handleStudentTest(HttpExchange exchange) throws IOException {
        User student = requireRole(exchange, "STUDENT");

        if (student == null) {
            return;
        }

        int testId = parseInt(parseQuery(exchange.getRequestURI()).get("testId"), 0);
        Test test = testService.findTest(testId);
        List<Question> questions = testService.getQuestionsForTest(testId);

        if (test == null) {
            sendHtml(exchange, buildMessagePage("Test not found", "Please choose another available test.", "/student"));
            return;
        }

        if (questions.isEmpty()) {
            sendHtml(exchange, buildMessagePage("No questions yet", "The teacher has not added questions to this test.", "/student"));
            return;
        }

        AttemptStartResult startResult = testService.startOrResumeAttempt(student, test, questions.size());

        if (!startResult.isAllowed()) {
            sendHtml(exchange, buildMessagePage("Test unavailable", startResult.getMessage(), "/student"));
            return;
        }

        sendHtml(exchange, buildAttemptPage(student, test, questions, startResult.getAttempt()));
    }

    private void handleStudentSubmit(HttpExchange exchange) throws IOException {
        User student = requireRole(exchange, "STUDENT");

        if (student == null) {
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }

        Map<String, String> formData = parseFormData(exchange);
        int testId = parseInt(formData.get("testId"), 0);
        int attemptId = parseInt(formData.get("attemptId"), 0);
        Test test = testService.findTest(testId);
        TestAttempt attempt = testService.findAttempt(attemptId);
        List<Question> questions = testService.getQuestionsForTest(testId);

        if (test == null || attempt == null || attempt.getTestId() != testId || attempt.getStudentId() != student.getId()) {
            sendHtml(exchange, buildMessagePage("Test not found", "Your submission could not be matched to a test.", "/student"));
            return;
        }

        if (attempt.isFinished()) {
            sendHtml(exchange, buildMessagePage("Test already attempted", "This test has already been submitted.", "/student"));
            return;
        }

        Map<Integer, String> answers = new HashMap<>();

        for (Question question : questions) {
            answers.put(question.getId(), formData.get("answer_" + question.getId()));
        }

        boolean late = System.currentTimeMillis() > attempt.getDeadlineMs() + 5_000L;
        int score = late ? 0 : testService.calculateScore(questions, answers);
        boolean saved = testService.submitAttempt(attempt, student, score, questions.size(), late);
        sendHtml(exchange, buildResultPage(student, test, score, questions.size(), saved, late));
    }

    private void handleStudentActivity(HttpExchange exchange) throws IOException {
        User student = requireRole(exchange, "STUDENT");

        if (student == null) {
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }

        Map<String, String> formData = parseFormData(exchange);
        int attemptId = parseInt(formData.get("attemptId"), 0);
        int testId = parseInt(formData.get("testId"), 0);
        testService.logSuspiciousActivity(attemptId, testId, student, formData.get("type"), formData.get("details"));
        sendNoContent(exchange);
    }

    private void handleStudentCancel(HttpExchange exchange) throws IOException {
        User student = requireRole(exchange, "STUDENT");

        if (student == null) {
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendMethodNotAllowed(exchange);
            return;
        }

        Map<String, String> formData = parseFormData(exchange);
        TestAttempt attempt = testService.findAttempt(parseInt(formData.get("attemptId"), 0));
        testService.cancelAttempt(attempt, student, formData.get("details"));
        sendNoContent(exchange);
    }

    private User requireRole(HttpExchange exchange, String role) throws IOException {
        User user = getAuthenticatedUser(exchange);

        if (user == null) {
            redirect(exchange, "/login");
            return null;
        }

        if (!role.equalsIgnoreCase(user.getRole())) {
            redirect(exchange, dashboardPath(user));
            return null;
        }

        return user;
    }

    private void sendOrRedirectAuthPage(HttpExchange exchange, User user, String mode, String message) throws IOException {
        if (user != null) {
            redirect(exchange, dashboardPath(user));
            return;
        }

        sendHtml(exchange, buildAuthPage(mode, message));
    }

    private String dashboardPath(User user) {
        return user != null && user.isTeacher() ? "/teacher" : "/student";
    }

    private void createSession(HttpExchange exchange, User user) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, user);
        exchange.getResponseHeaders().add("Set-Cookie",
                SESSION_COOKIE + "=" + sessionId + "; Path=/; HttpOnly; SameSite=Lax");
    }

    private User getAuthenticatedUser(HttpExchange exchange) {
        String sessionId = getCookieValue(exchange, SESSION_COOKIE);
        return sessionId == null ? null : sessions.get(sessionId);
    }

    private String getCookieValue(HttpExchange exchange, String cookieName) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");

        if (cookies == null) {
            return null;
        }

        for (String cookieHeader : cookies) {
            String[] cookiePairs = cookieHeader.split(";");

            for (String cookiePair : cookiePairs) {
                String[] keyValue = cookiePair.trim().split("=", 2);

                if (keyValue.length == 2 && cookieName.equals(keyValue[0])) {
                    return keyValue[1];
                }
            }
        }

        return null;
    }

    private Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
        Map<String, String> formData = new HashMap<>();
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        if (requestBody.isEmpty()) {
            return formData;
        }

        String[] pairs = requestBody.split("&");

        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            String key = decode(keyValue[0]);
            String value = keyValue.length > 1 ? decode(keyValue[1]) : "";
            formData.put(key, value);
        }

        return formData;
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> queryValues = new HashMap<>();
        String query = uri.getRawQuery();

        if (query == null || query.isEmpty()) {
            return queryValues;
        }

        for (String pair : query.split("&")) {
            String[] keyValue = pair.split("=", 2);
            String key = decode(keyValue[0]);
            String value = keyValue.length > 1 ? decode(keyValue[1]) : "";
            queryValues.put(key, value);
        }

        return queryValues;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String buildAuthPage(String mode, String message) {
        boolean registerMode = "register".equals(mode);
        String action = registerMode ? "/register" : "/login";
        String title = registerMode ? "Create Account" : "Login";
        String switchText = registerMode ? "Already have an account?" : "New user?";
        String switchLink = registerMode ? "/login" : "/register";
        String switchLabel = registerMode ? "Login" : "Create account";
        StringBuilder html = startPage(title);

        html.append("<main class=\"auth-shell\"><section class=\"panel auth-panel\">");
        html.append("<p class=\"eyebrow\">Role Based Quiz System</p>");
        html.append("<h1>").append(title).append("</h1>");
        appendAlert(html, message);
        html.append("<form class=\"form-grid\" method=\"post\" action=\"").append(action).append("\">");

        if (registerMode) {
            html.append(input("Name", "name", "text", true));
        }

        html.append(input("Username", "username", "text", true));
        html.append(input("Password", "password", "password", true));

        if (registerMode) {
            html.append("<label><span>Role</span><select name=\"role\" required>")
                    .append("<option value=\"STUDENT\">Student</option>")
                    .append("<option value=\"TEACHER\">Teacher</option>")
                    .append("</select></label>");
        }

        html.append("<button class=\"submit-button\" type=\"submit\">").append(title).append("</button>");
        html.append("</form><p class=\"switch-line\">").append(switchText).append(" <a href=\"").append(switchLink)
                .append("\">").append(switchLabel).append("</a></p></section></main>");
        return endPage(html);
    }

    private String buildTeacherDashboard(User teacher, List<Test> tests, List<TestResult> results,
            List<SuspiciousActivity> securityLogs, String message) {
        StringBuilder html = startPage("Teacher Dashboard");
        appendTopbar(html, "Teacher Dashboard", teacher);
        appendAlert(html, message);
        html.append("<section class=\"actions\"><a class=\"submit-button link-button\" href=\"/teacher/create\">Create New Test</a></section>");
        html.append("<section class=\"grid two\"><div class=\"panel\"><h2>Your Tests</h2>");

        if (tests.isEmpty()) {
            html.append("<p class=\"muted\">No tests created yet.</p>");
        } else {
            html.append("<div class=\"list\">");
            for (Test test : tests) {
                html.append("<article class=\"row\"><div><strong>").append(escapeHtml(test.getTitle())).append("</strong>")
                        .append("<p>").append(escapeHtml(test.getDescription())).append("</p>")
                        .append("<span>").append(test.getTimeLimitMinutes()).append(" minutes | ")
                        .append(test.getMaxAttempts()).append(" attempt(s)</span></div>")
                        .append("<a href=\"/teacher/question?testId=").append(test.getId()).append("\">Add Question</a></article>");
            }
            html.append("</div>");
        }

        html.append("</div><div class=\"panel\"><h2>Student Scores</h2>");

        if (results.isEmpty()) {
            html.append("<p class=\"muted\">No students have attempted your tests yet.</p>");
        } else {
            html.append("<table><thead><tr><th>Student</th><th>Test</th><th>Marks</th><th>Date</th></tr></thead><tbody>");
            for (TestResult result : results) {
                html.append("<tr><td>").append(escapeHtml(result.getStudentName())).append("<br><small>")
                        .append(escapeHtml(result.getStudentUsername())).append("</small></td><td>")
                        .append(escapeHtml(result.getTestTitle())).append("</td><td>")
                        .append(result.getScore()).append("/").append(result.getTotalQuestions()).append("</td><td>")
                        .append(escapeHtml(result.getTakenAt())).append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        html.append("</div></section><section class=\"panel security-panel\"><h2>Security Logs</h2>");

        if (securityLogs.isEmpty()) {
            html.append("<p class=\"muted\">No suspicious activity has been reported.</p>");
        } else {
            html.append("<table><thead><tr><th>Student</th><th>Test</th><th>Activity</th><th>Details</th><th>Date</th></tr></thead><tbody>");
            for (SuspiciousActivity log : securityLogs) {
                html.append("<tr><td>").append(escapeHtml(log.getStudentName())).append("<br><small>")
                        .append(escapeHtml(log.getStudentUsername())).append("</small></td><td>")
                        .append(escapeHtml(log.getTestTitle())).append("</td><td>")
                        .append(escapeHtml(log.getActivityType())).append("</td><td>")
                        .append(escapeHtml(log.getDetails())).append("</td><td>")
                        .append(escapeHtml(log.getCreatedAt())).append("</td></tr>");
            }
            html.append("</tbody></table>");
        }

        html.append("</section></main>");
        return endPage(html);
    }

    private String buildCreateTestPage(String message) {
        StringBuilder html = startPage("Create Test");
        appendSimpleNav(html, "Create Test", "/teacher");
        appendAlert(html, message);
        html.append("<section class=\"panel\"><form class=\"form-grid\" method=\"post\" action=\"/teacher/create\">")
                .append(input("Test title", "title", "text", true))
                .append("<label><span>Description</span><textarea name=\"description\" rows=\"4\"></textarea></label>")
                .append("<label><span>Time limit in minutes</span><input type=\"number\" min=\"1\" name=\"timeLimit\" value=\"10\" required></label>")
                .append("<label><span>Number of attempts allowed</span><input type=\"number\" min=\"1\" name=\"maxAttempts\" value=\"1\" required></label>")
                .append("<button class=\"submit-button\" type=\"submit\">Create Test</button>")
                .append("</form></section></main>");
        return endPage(html);
    }

    private String buildQuestionPage(Test test, String message) {
        StringBuilder html = startPage("Add Question");
        appendSimpleNav(html, "Add Question", "/teacher");
        appendAlert(html, message);
        html.append("<section class=\"panel\"><p class=\"eyebrow\">").append(escapeHtml(test.getTitle())).append("</p>")
                .append("<form class=\"form-grid\" method=\"post\" action=\"/teacher/question?testId=").append(test.getId()).append("\">")
                .append("<label><span>Question</span><textarea name=\"question\" rows=\"4\" required></textarea></label>")
                .append(input("Option A", "optionA", "text", true))
                .append(input("Option B", "optionB", "text", true))
                .append(input("Option C", "optionC", "text", true))
                .append(input("Option D", "optionD", "text", true))
                .append("<label><span>Correct answer</span><select name=\"correctAnswer\" required>")
                .append("<option value=\"A\">A</option><option value=\"B\">B</option><option value=\"C\">C</option><option value=\"D\">D</option>")
                .append("</select></label><button class=\"submit-button\" type=\"submit\">Add Question</button>")
                .append("</form></section></main>");
        return endPage(html);
    }

    private String buildStudentDashboard(User student, List<Test> tests) {
        StringBuilder html = startPage("Student Dashboard");
        appendTopbar(html, "Available Tests", student);
        html.append("<section class=\"panel\"><div class=\"list\">");

        if (tests.isEmpty()) {
            html.append("<p class=\"muted\">No tests are available right now.</p>");
        } else {
            for (Test test : tests) {
                html.append("<article class=\"row\"><div><strong>").append(escapeHtml(test.getTitle())).append("</strong>")
                        .append("<p>").append(escapeHtml(test.getDescription())).append("</p>")
                        .append("<span>").append(test.getTimeLimitMinutes()).append(" minutes | ")
                        .append(test.getMaxAttempts()).append(" attempt(s)</span></div>")
                        .append("<a class=\"submit-button small\" href=\"/student/test?testId=").append(test.getId()).append("\">Attempt</a></article>");
            }
        }

        html.append("</div></section></main>");
        return endPage(html);
    }

    private String buildAttemptPage(User student, Test test, List<Question> questions, TestAttempt attempt) {
        StringBuilder html = startPage(test.getTitle());
        appendTopbar(html, test.getTitle(), student);
        long remainingSeconds = Math.max(0, (attempt.getDeadlineMs() - System.currentTimeMillis()) / 1000);
        html.append("<section class=\"exam-lock\" id=\"examLock\"><h2>Mandatory Exam Mode</h2><p>Questions are hidden until fullscreen starts. If you exit fullscreen after the test starts, this attempt will be canceled.</p>")
                .append("<button class=\"submit-button\" type=\"button\" id=\"startExam\">Start Fullscreen Test</button></section>");
        html.append("<section class=\"timer-bar\"><strong id=\"timer\"></strong><span>")
                .append(test.getTimeLimitMinutes()).append(" minute test</span><span>Attempt ")
                .append(attempt.getAttemptNumber()).append(" of ").append(test.getMaxAttempts()).append("</span></section>");
        html.append("<form id=\"testForm\" class=\"quiz-form locked-test\" method=\"post\" action=\"/student/submit\">")
                .append("<input type=\"hidden\" name=\"testId\" value=\"").append(test.getId()).append("\">")
                .append("<input type=\"hidden\" name=\"attemptId\" value=\"").append(attempt.getId()).append("\">");

        for (int index = 0; index < questions.size(); index++) {
            appendQuestion(html, index + 1, questions.get(index));
        }

        html.append("<button class=\"submit-button\" type=\"submit\">Submit Test</button></form>");
        html.append("<script>")
                .append("let remaining=").append(remainingSeconds).append(";")
                .append("let submitted=false;let examStarted=false;let ticking=false;")
                .append("const attemptId=").append(attempt.getId()).append(";const testId=").append(test.getId()).append(";")
                .append("const timer=document.getElementById('timer');const form=document.getElementById('testForm');")
                .append("const startButton=document.getElementById('startExam');const examLock=document.getElementById('examLock');")
                .append("function data(type,details){const body='attemptId='+attemptId+'&testId='+testId+'&type='+encodeURIComponent(type)+'&details='+encodeURIComponent(details||'');")
                .append("if(navigator.sendBeacon){navigator.sendBeacon('/student/activity',new Blob([body],{type:'application/x-www-form-urlencoded'}));}")
                .append("else{fetch('/student/activity',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body,keepalive:true});}}")
                .append("function cancelAttempt(details){if(submitted)return;submitted=true;data('FULLSCREEN_EXIT_CANCEL',details);")
                .append("const body='attemptId='+attemptId+'&details='+encodeURIComponent(details);")
                .append("if(navigator.sendBeacon){navigator.sendBeacon('/student/cancel',new Blob([body],{type:'application/x-www-form-urlencoded'}));}")
                .append("else{fetch('/student/cancel',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body,keepalive:true});}")
                .append("alert('Test canceled because fullscreen was exited.');window.location.href='/student';}")
                .append("function tick(){const m=Math.floor(remaining/60);const s=String(remaining%60).padStart(2,'0');timer.textContent=m+':'+s;if(!examStarted)return;if(remaining<=0){submitted=true;form.submit();return;}remaining--;}")
                .append("tick();setInterval(tick,1000);")
                .append("function showQuestions(){examStarted=true;form.classList.remove('locked-test');examLock.classList.add('hidden');data('FULLSCREEN_STARTED','Student started fullscreen exam mode.');}")
                .append("startButton.addEventListener('click',async()=>{try{await document.documentElement.requestFullscreen?.();}catch(e){alert('Fullscreen permission is required to view questions.');}});")
                .append("document.addEventListener('fullscreenchange',()=>{if(document.fullscreenElement){showQuestions();}else if(examStarted&&!submitted){cancelAttempt('Student exited fullscreen mode.');}});")
                .append("document.addEventListener('visibilitychange',()=>{if(document.hidden&&!submitted){data('TAB_SWITCH','Student switched tab or minimized browser.');}});")
                .append("window.history.pushState(null,'',window.location.href);window.addEventListener('popstate',()=>{window.history.pushState(null,'',window.location.href);data('BACK_BUTTON','Student tried to navigate back during the test.');alert('Back navigation is disabled during the test.');});")
                .append("window.addEventListener('beforeunload',(event)=>{if(!submitted){data('RELOAD_OR_EXIT','Student tried to reload, close, or leave the test page.');event.preventDefault();event.returnValue='Test is in progress.';}});")
                .append("document.addEventListener('keydown',(event)=>{const k=event.key.toLowerCase();if(k==='f5'||(event.ctrlKey&&k==='r')||(event.altKey&&k==='arrowleft')){event.preventDefault();data('BLOCKED_KEY','Student used blocked navigation/reload shortcut.');alert('This shortcut is blocked during the test.');}});")
                .append("form.addEventListener('submit',()=>{submitted=true;});")
                .append("</script>");
        html.append("</main>");
        return endPage(html);
    }

    private void appendQuestion(StringBuilder html, int questionNumber, Question question) {
        String fieldName = "answer_" + question.getId();
        html.append("<fieldset class=\"question-card\"><legend>Question ").append(questionNumber).append("</legend><h2>")
                .append(escapeHtml(question.getQuestion())).append("</h2>");
        appendOption(html, fieldName, "A", question.getOptionA());
        appendOption(html, fieldName, "B", question.getOptionB());
        appendOption(html, fieldName, "C", question.getOptionC());
        appendOption(html, fieldName, "D", question.getOptionD());
        html.append("</fieldset>");
    }

    private void appendOption(StringBuilder html, String fieldName, String value, String label) {
        html.append("<label class=\"option-row\"><input type=\"radio\" name=\"").append(fieldName).append("\" value=\"")
                .append(value).append("\" required><span class=\"option-letter\">").append(value).append("</span><span>")
                .append(escapeHtml(label)).append("</span></label>");
    }

    private String buildResultPage(User student, Test test, int score, int totalQuestions, boolean saved, boolean late) {
        int percentage = totalQuestions == 0 ? 0 : (int) Math.round((score * 100.0) / totalQuestions);
        StringBuilder html = startPage("Result");
        html.append("<main class=\"shell result-shell\"><section class=\"panel result-panel\"><p class=\"eyebrow\">Test Completed</p>")
                .append("<h1>").append(escapeHtml(test.getTitle())).append("</h1>")
                .append("<div class=\"score-ring\"><strong>").append(score).append("</strong><span>out of ")
                .append(totalQuestions).append("</span></div><p class=\"result-text\">")
                .append(escapeHtml(student.getName())).append(", you scored ").append(percentage).append("%.</p>")
                .append("<p class=\"muted\">").append(saved ? "Result saved to database." : "Result could not be saved.")
                .append(late ? " Submitted after the time limit." : "").append("</p>")
                .append("<a class=\"submit-button link-button\" href=\"/student\">Back to Tests</a></section></main>");
        return endPage(html);
    }

    private String buildMessagePage(String title, String message, String href) {
        StringBuilder html = startPage(title);
        html.append("<main class=\"shell result-shell\"><section class=\"panel result-panel\"><h1>")
                .append(escapeHtml(title)).append("</h1><p class=\"muted\">").append(escapeHtml(message))
                .append("</p><a class=\"submit-button link-button\" href=\"").append(href).append("\">Go Back</a></section></main>");
        return endPage(html);
    }

    private void appendTopbar(StringBuilder html, String title, User user) {
        html.append("<main class=\"shell\"><section class=\"topbar\"><div><p class=\"eyebrow\">")
                .append(escapeHtml(user.getRole())).append("</p><h1>").append(escapeHtml(title))
                .append("</h1></div><div class=\"account-box\"><span>").append(escapeHtml(user.getName()))
                .append("</span><a href=\"/logout\">Logout</a></div></section>");
    }

    private void appendSimpleNav(StringBuilder html, String title, String backHref) {
        html.append("<main class=\"shell\"><section class=\"topbar\"><div><p class=\"eyebrow\">Quiz System</p><h1>")
                .append(escapeHtml(title)).append("</h1></div><div class=\"account-box\"><a href=\"")
                .append(backHref).append("\">Dashboard</a><a href=\"/logout\">Logout</a></div></section>");
    }

    private void appendAlert(StringBuilder html, String message) {
        if (message != null && !message.isEmpty()) {
            html.append("<p class=\"alert\">").append(escapeHtml(message)).append("</p>");
        }
    }

    private String input(String label, String name, String type, boolean required) {
        return "<label><span>" + label + "</span><input type=\"" + type + "\" name=\"" + name + "\""
                + (required ? " required" : "") + "></label>";
    }

    private StringBuilder startPage(String title) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<title>").append(escapeHtml(title)).append("</title>").append(getStyles()).append("</head><body>");
        return html;
    }

    private String endPage(StringBuilder html) {
        html.append("</body></html>");
        return html.toString();
    }

    private String getStyles() {
        return "<style>"
                + "*{box-sizing:border-box}body{margin:0;font-family:Arial,Helvetica,sans-serif;background:#f4f7f5;color:#17201b}"
                + "a{color:#246b5a;font-weight:700}.shell{width:min(1100px,92vw);margin:0 auto;padding:32px 0 48px}"
                + ".auth-shell{min-height:100vh;display:grid;place-items:center;padding:24px}.panel{background:#fff;border:1px solid #cad6cf;border-radius:8px;padding:22px}"
                + ".auth-panel{width:min(440px,100%)}.topbar{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:24px;border-bottom:1px solid #cad6cf;padding-bottom:18px}"
                + ".eyebrow{margin:0 0 6px;color:#2f6f5e;font-size:13px;font-weight:700;text-transform:uppercase;letter-spacing:0}h1{margin:0;font-size:clamp(28px,5vw,44px);line-height:1.08}h2{margin:0 0 14px;font-size:22px}"
                + ".account-box{display:flex;align-items:center;gap:12px;border:1px solid #98b0a5;background:#fff;padding:8px 12px;border-radius:8px;font-size:14px}"
                + ".form-grid,.quiz-form{display:grid;gap:16px}.form-grid label{display:grid;gap:7px;font-weight:700}"
                + "input,select,textarea{width:100%;border:1px solid #aabbb2;border-radius:6px;padding:12px 14px;font-size:16px;font-family:inherit}"
                + ".submit-button{border:0;border-radius:8px;background:#246b5a;color:#fff;padding:13px 18px;font-size:16px;font-weight:700;cursor:pointer;text-align:center;text-decoration:none}.submit-button:hover{background:#1a5748}.small{padding:10px 14px}"
                + ".link-button{display:inline-block}.actions{margin-bottom:18px}.grid.two{display:grid;grid-template-columns:1fr 1.2fr;gap:18px}.list{display:grid;gap:12px}.row{display:flex;align-items:center;justify-content:space-between;gap:12px;border:1px solid #d8e2dd;border-radius:8px;padding:14px;background:#fbfcfb}.row p{margin:4px 0;color:#52635b}"
                + ".question-card{border:1px solid #cad6cf;border-radius:8px;background:#fff;padding:18px;display:grid;gap:12px}legend{padding:0 8px;color:#2f6f5e;font-weight:700}.option-row{display:grid;grid-template-columns:20px 36px 1fr;align-items:center;gap:10px;border:1px solid #d8e2dd;border-radius:8px;padding:12px;cursor:pointer;background:#fbfcfb}.option-row:hover{border-color:#2f6f5e;background:#f0f7f4}.option-letter{display:inline-grid;place-items:center;width:30px;height:30px;border-radius:50%;background:#e7efeb;color:#214f43;font-weight:700}"
                + ".timer-bar{position:sticky;top:0;z-index:1;display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;border:1px solid #cad6cf;background:#fff;border-radius:8px;padding:14px 18px}.timer-bar strong{font-size:24px;color:#7a2e1f}"
                + ".exam-lock{border:1px solid #e3b4a8;background:#fff3ef;border-radius:8px;padding:18px;margin-bottom:16px}.exam-lock h2{margin:0 0 8px}.exam-lock p{margin:0 0 14px;color:#7a2e1f}.security-panel{margin-top:18px}"
                + ".locked-test{display:none}.hidden{display:none}"
                + ".result-shell{min-height:100vh;display:grid;place-items:center}.result-panel{width:min(560px,100%);text-align:center}.score-ring{width:160px;height:160px;margin:24px auto;display:grid;place-items:center;border:10px solid #2f6f5e;border-radius:50%;background:#f7fbf9}.score-ring strong{display:block;font-size:48px;line-height:1}.score-ring span{font-size:15px;color:#52635b}.result-text{font-size:20px;margin:0 0 12px}.muted{color:#52635b}.alert{border:1px solid #e3b4a8;background:#fff3ef;color:#7a2e1f;border-radius:8px;padding:12px;margin:0 0 18px}.switch-line{margin:18px 0 0;text-align:center;color:#52635b}"
                + "table{width:100%;border-collapse:collapse}th,td{text-align:left;border-bottom:1px solid #d8e2dd;padding:10px;vertical-align:top}small{color:#52635b}"
                + "@media(max-width:760px){.topbar,.row{align-items:flex-start;flex-direction:column}.grid.two{grid-template-columns:1fr}.account-box{width:100%;justify-content:space-between}.shell{padding-top:22px}}"
                + "</style>";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        byte[] response = "Method not allowed".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(405, response.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}
