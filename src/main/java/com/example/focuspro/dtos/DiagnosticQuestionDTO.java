package com.example.focuspro.dtos;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DiagnosticQuestionDTO {
    private Integer id;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String dimension;
    private Short displayOrder;
}