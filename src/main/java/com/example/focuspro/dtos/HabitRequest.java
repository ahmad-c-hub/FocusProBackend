package com.example.focuspro.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HabitRequest {
    private String title;
    private String description;
    private int durationMinutes = 10;
    private int frequencyPerWeek = 1;
    private boolean monday;
    private boolean tuesday;
    private boolean wednesday;
    private boolean thursday;
    private boolean friday;
    private boolean saturday;
    private boolean sunday;
}
