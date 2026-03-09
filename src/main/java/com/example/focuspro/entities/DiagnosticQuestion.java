package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "diagnostic_question")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DiagnosticQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "option_a", nullable = false, length = 300)
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 300)
    private String optionB;

    @Column(name = "option_c", nullable = false, length = 300)
    private String optionC;

    @Column(name = "option_d", nullable = false, length = 300)
    private String optionD;

    @Column(name = "points_a", nullable = false)
    private Short pointsA;

    @Column(name = "points_b", nullable = false)
    private Short pointsB;

    @Column(name = "points_c", nullable = false)
    private Short pointsC;

    @Column(name = "points_d", nullable = false)
    private Short pointsD;

    @Column(name = "dimension", nullable = false, length = 50)
    private String dimension;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    @Column(name = "created_at", updatable = false, insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private java.time.LocalDateTime createdAt;
}