package com.example.focuspro.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HabitLogRequest {
    private boolean completed;
    private Integer timeSpentMinutes;
}
