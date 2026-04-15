package com.example.focuspro.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DailyGoalDTO {

    private long id;
    private String goalText;
    private String status;
    private LocalDate goalDate;
    private LocalDateTime createdAt;

    public DailyGoalDTO() {}

    public DailyGoalDTO(long id, String goalText, String status,
                        LocalDate goalDate, LocalDateTime createdAt) {
        this.id = id;
        this.goalText = goalText;
        this.status = status;
        this.goalDate = goalDate;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getGoalText() { return goalText; }
    public void setGoalText(String goalText) { this.goalText = goalText; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getGoalDate() { return goalDate; }
    public void setGoalDate(LocalDate goalDate) { this.goalDate = goalDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
