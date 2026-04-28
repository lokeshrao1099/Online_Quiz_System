package database;

import java.util.List;

import model.Question;

public interface QuestionRepository {
    List<Question> getAllQuestions();
}
