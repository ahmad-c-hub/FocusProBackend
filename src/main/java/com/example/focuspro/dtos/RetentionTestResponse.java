package com.example.focuspro.dtos;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class RetentionTestResponse {
    private int                     correctCount;
    private int                     totalQuestions;
    private double                  scoreDelta;
    private double                  newFocusScore;
    private List<AiAnswerResultDTO> results;
    private String                  message;
}
