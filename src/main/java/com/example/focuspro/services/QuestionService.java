package com.example.focuspro.services;

import com.example.focuspro.dtos.QuestionDTO;
import com.example.focuspro.entities.Question;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.QuestionRepo;
import com.example.focuspro.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionService {

    @Autowired
    private QuestionRepo questionRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    public List<QuestionDTO> getFirstTestQuestions(String level) {
        Users usersNavigating = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        List<Question> questions = questionRepo.findAll();
        List<QuestionDTO> dtos = new ArrayList<>();
        for (Question question : questions) {
            QuestionDTO dto = new QuestionDTO(
                    question.getId(),
                    question.getQuestionText(),
                    question.getOptionA(),
                    question.getOptionB(),
                    question.getOptionC(),
                    question.getOptionD(),
                    question.getPictureUrl(),
                    question.getType(),
                    question.getLevel()
            );
            dtos.add(dto);
        }
        return dtos;
    }


    public void saveAnswer(int questionId, String selectedAnswer) {
        Users userNavigating = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        jdbcTemplate.update(
                "INSERT INTO answered (user_id, question_id, selected_answer, is_correct) VALUES (?, ?, ?, ?)",
                userNavigating.getId(),
                questionId,
                selectedAnswer.toUpperCase(),
                false
        );
    }


    public int getAnswerScore(String answer) {
        return switch (answer.toUpperCase()) {
            case "A" -> 4;
            case "B" -> 3;
            case "C" -> 2;
            case "D" -> 1;
            default -> 0;
        };
    }


    public String submitBaselineTestResults(int rawScore) {
        Users userNavigating = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        double normalizedScore = (rawScore / 60.0) * 100.0;
        String focusTier = getFocusTier(rawScore);

        userNavigating.setFocusScore(normalizedScore);
        userRepo.save(userNavigating);

        return String.format(
                "Baseline test complete! Raw score: %d/60 | Focus score: %.1f/100 | Tier: %s",
                rawScore, normalizedScore, focusTier
        );
    }


    private String getFocusTier(int rawScore) {
        if (rawScore >= 49) return "Focus Master";
        if (rawScore >= 37) return "Focused Learner";
        if (rawScore >= 25) return "Aware Learner";
        if (rawScore >= 13) return "Distracted Mind";
        return "Scroll Addict";
    }
}