package com.example.focuspro.dtos;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DiagnosticAnswerDTO {
    private Integer questionId;
    private String selectedOption; // "A", "B", "C", or "D"
    private int pointsEarned;
}