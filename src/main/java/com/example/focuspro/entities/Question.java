package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "questions")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "option_a", nullable = false)
    private String optionA;

    @Column(name = "option_b", nullable = false)
    private String optionB;

    @Column(name = "option_c", nullable = false)
    private String optionC;

    @Column(name = "option_d", nullable = false)
    private String optionD;

    // Store the correct option as "A", "B", "C", or "D"
    @Column(name = "correct_answer", nullable = false, length = 1)
    private String correctAnswer;

    // Optional picture for the question (can be null)
    @Column(name = "picture_url", length = 500)
    private String pictureUrl;

    // Types:stroop, nback, reaction, iq_pattern, iq_verbal, iq_matrix
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    // Levels: baseline, easy, medium, hard
    @Column(name = "level", nullable = false, length = 50)
    private String level;

    @Column(name = "created_at", updatable = false, insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private java.time.LocalDateTime createdAt;
}
