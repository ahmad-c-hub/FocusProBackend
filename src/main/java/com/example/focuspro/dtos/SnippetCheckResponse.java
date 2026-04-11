package com.example.focuspro.dtos;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class SnippetCheckResponse {
    private int                     correctCount;
    private int                     totalQuestions;
    private boolean                 passed;
    private double                  focusScoreGained;
    private double                  newFocusScore;
    private List<AiAnswerResultDTO> results;
    private String                  message;
}
