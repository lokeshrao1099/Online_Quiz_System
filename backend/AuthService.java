package backend;

import java.sql.SQLException;

import database.UserDAO;
import model.AuthResult;
import model.User;

public class AuthService {
    private final UserDAO userDAO;

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public AuthResult register(String name, String username, String password, String role) {
        String cleanName = clean(name);
        String cleanUsername = clean(username).toLowerCase();
        String cleanRole = clean(role).toUpperCase();

        if (cleanName.isEmpty()) {
            return AuthResult.failure("Please enter your name.");
        }

        if (!cleanUsername.matches("[a-z0-9_]{3,30}")) {
            return AuthResult.failure("Username must be 3-30 characters and use letters, numbers, or underscore.");
        }

        if (password == null || password.length() < 6) {
            return AuthResult.failure("Password must be at least 6 characters.");
        }

        if (!"TEACHER".equals(cleanRole) && !"STUDENT".equals(cleanRole)) {
            return AuthResult.failure("Please select Teacher or Student.");
        }

        try {
            if (userDAO.findByUsername(cleanUsername) != null) {
                return AuthResult.failure("An account with this username already exists.");
            }

            User user = new User();
            user.setName(cleanName);
            user.setUsername(cleanUsername);
            user.setEmail(cleanUsername + "@local.quiz");
            user.setRole(cleanRole);
            user.setPasswordHash(PasswordUtil.hashPassword(password));
            userDAO.createUser(user);

            User createdUser = userDAO.findByUsername(cleanUsername);
            return AuthResult.success("Account created successfully.", createdUser);
        } catch (SQLException exception) {
            return AuthResult.failure("Database connection failed: " + exception.getMessage());
        }
    }

    public AuthResult login(String username, String password) {
        String cleanUsername = clean(username).toLowerCase();

        if (cleanUsername.isEmpty() || password == null || password.isEmpty()) {
            return AuthResult.failure("Please enter your username and password.");
        }

        try {
            User user = userDAO.findByUsername(cleanUsername);

            if (user == null || !PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
                return AuthResult.failure("Invalid username or password.");
            }

            return AuthResult.success("Login successful.", user);
        } catch (SQLException exception) {
            return AuthResult.failure("Database connection failed: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return AuthResult.failure("Stored password format is invalid.");
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
