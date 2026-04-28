# Java OOP Quiz Application

This project runs as a browser app on `http://localhost:8080` using Java's built-in HTTP server.

## Project Layers

- `frontend` - browser and console UI classes
- `backend` - quiz, login, and password hashing services
- `database` - JDBC connection, DAO classes, and SQL schema
- `model` - POJO/data classes

## MySQL Setup

1. Open MySQL Workbench or another MySQL client.
2. Run the SQL file:

```sql
source database/schema.sql;
```

If your MySQL client does not support `source`, open `database/schema.sql` and execute the full file.

## Add MySQL Connector/J

Download MySQL Connector/J from the official MySQL website and place the jar in this project. This project currently has:

```text
mysql-connector-j-9.7.0/mysql-connector-j-9.7.0.jar
```

## Compile

```powershell
javac -cp ".;mysql-connector-j-9.7.0/mysql-connector-j-9.7.0.jar" AppLauncher.java frontend/*.java backend/*.java database/*.java model/*.java
```

## Run

For default MySQL settings:

```powershell
java -cp ".;mysql-connector-j-9.7.0/mysql-connector-j-9.7.0.jar" AppLauncher
```

Then open:

```text
http://localhost:8080
```

For a custom database user/password:

```powershell
java -Dquiz.db.user="root" -Dquiz.db.password="your_password" -cp ".;mysql-connector-j-9.7.0/mysql-connector-j-9.7.0.jar" AppLauncher
```

You can also override the database URL:

```powershell
java -Dquiz.db.url="jdbc:mysql://localhost:3306/quiz_app" -Dquiz.db.user="root" -Dquiz.db.password="your_password" -cp ".;mysql-connector-j-9.7.0/mysql-connector-j-9.7.0.jar" AppLauncher
```

## Role-Based Flow

1. Open `http://localhost:8080`.
2. Create an account as either `Teacher` or `Student`.
3. Teachers are redirected to the teacher dashboard where they can create tests, add MCQ questions, set correct answers, set a time limit, and view student scores.
4. Students are redirected to the student dashboard where they can view available tests and attempt them inside the time limit.
5. Student scores are saved in the `test_results` table and the latest score is stored on the `users` table.

## Exam Security

- Each student gets one database-backed attempt per test.
- Teachers can set the number of attempts allowed for each test.
- Reloading or opening the same test again resumes the active attempt while time remains.
- Submitted, expired, or canceled attempts count toward the attempt limit.
- The timer is enforced by the server deadline and the browser auto-submits when time ends.
- The exam page hides all questions until fullscreen mode starts.
- Exiting fullscreen cancels the current attempt and logs the action for the teacher.
- The app logs suspicious activity such as tab switch, back navigation, reload/exit attempts, and blocked shortcut keys.
- Teachers can review suspicious activity from the teacher dashboard.
