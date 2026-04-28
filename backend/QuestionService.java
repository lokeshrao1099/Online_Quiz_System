package backend;

import java.util.List;

import database.QuestionRepository;
import model.Question;

public class QuestionService {
    private final QuestionRepository questionRepository;

    public QuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public List<Question> getQuestions() {
        return questionRepository.getAllQuestions();
    }
}
