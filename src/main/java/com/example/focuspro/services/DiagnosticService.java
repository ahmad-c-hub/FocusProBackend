package com.example.focuspro.services;

import com.example.focuspro.dtos.*;
import com.example.focuspro.entities.DiagnosticQuestion;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.DiagnosticQuestionRepo;
import com.example.focuspro.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DiagnosticService {

    @Autowired
    private DiagnosticQuestionRepo diagnosticQuestionRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    public List<DiagnosticQuestionDTO> getAllQuestions() {
        List<DiagnosticQuestion> questions = diagnosticQuestionRepo.findAllByOrderByDisplayOrderAsc();
        return questions.stream().map(q -> new DiagnosticQuestionDTO(
                q.getId(),
                q.getQuestionText(),
                q.getOptionA(),
                q.getOptionB(),
                q.getOptionC(),
                q.getOptionD(),
                q.getDimension(),
                q.getDisplayOrder()
        )).toList();
    }


    @Transactional
    public String submitDiagnostic(DiagnosticSubmitRequest request) {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 1. Insert diagnostic_session
        int sessionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO diagnostic_session 
                    (user_id, screen_score, attention_score, lifestyle_score, learning_score, raw_total, focus_score)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Integer.class,
                user.getId(),
                request.getScreenScore(),
                request.getAttentionScore(),
                request.getLifestyleScore(),
                request.getLearningScore(),
                request.getRawTotal(),
                request.getFocusScore()
        );

        // 2. Insert each answer into diagnostic_response
        for (DiagnosticAnswerDTO answer : request.getAnswers()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO diagnostic_response 
                        (session_id, question_id, selected_option, points_earned)
                    VALUES (?, ?, ?, ?)
                    """,
                    sessionId,
                    answer.getQuestionId(),
                    answer.getSelectedOption().toUpperCase(),
                    answer.getPointsEarned()
            );
        }

        // 3. Update user's focus score
        user.setFocusScore(request.getFocusScore());
        userRepo.save(user);

        return String.format(
                "Diagnostic complete! Focus score: %.1f/100 | Tier: %s",
                request.getFocusScore(),
                getFocusTier(request.getRawTotal())
        );
    }


    private String getFocusTier(double rawTotal) {
        if (rawTotal >= 49) return "Focus Master";
        if (rawTotal >= 37) return "Focused Learner";
        if (rawTotal >= 25) return "Aware Learner";
        if (rawTotal >= 13) return "Distracted Mind";
        return "Scroll Addict";
    }
}