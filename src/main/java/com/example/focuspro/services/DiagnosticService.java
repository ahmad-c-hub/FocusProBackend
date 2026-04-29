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
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DiagnosticService {

    @Autowired
    private DiagnosticQuestionRepo diagnosticQuestionRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ActivityLogService activityLogService;


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

        // 1. Load questions from DB to compute scores server-side
        Map<Integer, DiagnosticQuestion> questionMap = diagnosticQuestionRepo
                .findAllByOrderByDisplayOrderAsc()
                .stream()
                .collect(Collectors.toMap(q -> q.getId().intValue(), q -> q));

        // 2. Compute dimension raw scores from answers
        double screenRaw = 0, attentionRaw = 0, lifestyleRaw = 0, learningRaw = 0;
        for (DiagnosticAnswerDTO answer : request.getAnswers()) {
            DiagnosticQuestion q = questionMap.get(answer.getQuestionId());
            if (q == null) continue;
            int pts = pointsForOption(q, answer.getSelectedOption());
            answer.setPointsEarned(pts); // update DTO so DB insert uses computed value
            switch (q.getDimension()) {
                case "screen_habits" -> screenRaw    += pts;
                case "attention"     -> attentionRaw += pts;
                case "lifestyle"     -> lifestyleRaw += pts;
                case "learning"      -> learningRaw  += pts;
            }
        }

        double rawTotal   = screenRaw + attentionRaw + lifestyleRaw + learningRaw;
        double focusScore = Math.round(40 + (rawTotal / 59.0) * 60);

        // 3. Insert diagnostic_session with server-computed scores
        int sessionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO diagnostic_session
                    (user_id, screen_score, attention_score, lifestyle_score, learning_score, raw_total, focus_score)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Integer.class,
                user.getId(),
                (screenRaw    / 16.0) * 100,
                (attentionRaw / 25.0) * 100,
                (lifestyleRaw /  9.0) * 100,
                (learningRaw  /  9.0) * 100,
                rawTotal,
                focusScore
        );

        // 4. Insert each answer into diagnostic_response
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

        // 5. Update user's focus score and seed the long-term score
        user.setFocusScore(focusScore);
        // Seed long_term_score from the diagnostic result so the profile
        // shows a score immediately without waiting for the midnight scheduler.
        if (user.getLongTermScore() == null || user.getLongTermScore() == 0.0) {
            user.setLongTermScore(focusScore);
        }
        userRepo.save(user);

        activityLogService.log(
                user.getId(),
                "DIAGNOSTIC_COMPLETE",
                String.format("Completed diagnostic assessment. Focus score: %.1f/100", focusScore),
                String.format("{\"focusScore\":%.1f,\"rawTotal\":%.1f,\"tier\":\"%s\"}",
                        focusScore, rawTotal, getFocusTier(rawTotal))
        );

        return String.format(
                "Diagnostic complete! Focus score: %.1f/100 | Tier: %s",
                focusScore,
                getFocusTier(rawTotal)
        );
    }

    /** Looks up the points for a given answer option directly from the DB entity. */
    private int pointsForOption(DiagnosticQuestion q, String option) {
        return switch (option.toUpperCase()) {
            case "A" -> q.getPointsA();
            case "B" -> q.getPointsB();
            case "C" -> q.getPointsC();
            case "D" -> q.getPointsD();
            default  -> 0;
        };
    }


    private String getFocusTier(double rawTotal) {
        if (rawTotal >= 49) return "Focus Master";
        if (rawTotal >= 37) return "Focused Learner";
        if (rawTotal >= 25) return "Aware Learner";
        if (rawTotal >= 13) return "Distracted Mind";
        return "Scroll Addict";
    }
}