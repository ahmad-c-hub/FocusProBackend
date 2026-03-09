package com.example.focuspro.dtos;

import com.example.focuspro.dtos.DiagnosticAnswerDTO;
import lombok.*;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DiagnosticSubmitRequest {
    private List<DiagnosticAnswerDTO> answers; // one per question
    private double focusScore;                 // final normalized score calculated by frontend
    private double rawTotal;
    private double screenScore;
    private double attentionScore;
    private double lifestyleScore;
    private double learningScore;
}