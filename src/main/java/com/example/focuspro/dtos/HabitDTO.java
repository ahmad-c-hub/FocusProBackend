package com.example.focuspro.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HabitDTO {
    private int id;
    private String title;
    private String description;
    private int durationMinutes;
    private int frequencyPerWeek;
    private boolean monday;
    private boolean tuesday;
    private boolean wednesday;
    private boolean thursday;
    private boolean friday;
    private boolean saturday;
    private boolean sunday;
    // Computed from habit_logs
    private boolean doneToday;
    private int streak;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
