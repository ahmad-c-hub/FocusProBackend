package com.example.focuspro.dtos;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Map;

@Getter
@NoArgsConstructor
public class AiAnswerRequest {
    private Map<Long, String> answers;
}
