package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_generated_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiGeneratedQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which user this question was generated for (questions are personalised per user)
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    // The snippet this question is about (null for retention-test questions)
    @Column(name = "snippet_id")
    private Integer snippetId;

    // SNIPPET  → generated right after finishing a snippet
    // RETENTION → part of a periodic retention audit
    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "option_a", nullable = false, columnDefinition = "TEXT")
    private String optionA;

    @Column(name = "option_b", nullable = false, columnDefinition = "TEXT")
    private String optionB;

    @Column(name = "option_c", nullable = false, columnDefinition = "TEXT")
    private String optionC;

    @Column(name = "option_d", nullable = false, columnDefinition = "TEXT")
    private String optionD;

    // Stores "A", "B", "C", or "D"
    @Column(name = "correct_answer", nullable = false, length = 1)
    private String correctAnswer;

    // Difficulty 1-3 matching the book's level so the AI scales with the user
    @Column(name = "difficulty_level")
    private Integer difficultyLevel;

    @Column(name = "created_at", updatable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
}
