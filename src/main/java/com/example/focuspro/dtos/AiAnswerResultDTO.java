package com.example.focuspro.dtos;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiAnswerResultDTO {
    private Long    questionId;
    private String  chosenAnswer;
    private String  correctAnswer;
    private boolean correct;
}
