package com.example.focuspro.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class FocusScheduleDTO {

    private Long id;
    private int userId;
    private String scheduleType;
    private String scheduledTime;
    private int durationMinutes;
    private int prepTimerMinutes;
    private boolean isRecurring;
    private String daysOfWeek;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime lastTriggeredAt;

    public FocusScheduleDTO() {}

    public FocusScheduleDTO(Long id, int userId, String scheduleType, String scheduledTime,
                             int durationMinutes, int prepTimerMinutes, boolean isRecurring,
                             String daysOfWeek, boolean isActive, LocalDateTime createdAt,
                             LocalDateTime lastTriggeredAt) {
        this.id = id;
        this.userId = userId;
        this.scheduleType = scheduleType;
        this.scheduledTime = scheduledTime;
        this.durationMinutes = durationMinutes;
        this.prepTimerMinutes = prepTimerMinutes;
        this.isRecurring = isRecurring;
        this.daysOfWeek = daysOfWeek;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.lastTriggeredAt = lastTriggeredAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }

    public String getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public int getPrepTimerMinutes() { return prepTimerMinutes; }
    public void setPrepTimerMinutes(int prepTimerMinutes) { this.prepTimerMinutes = prepTimerMinutes; }

    @JsonProperty("isRecurring")
    public boolean isRecurring() { return isRecurring; }
    @JsonProperty("isRecurring")
    public void setRecurring(boolean recurring) { isRecurring = recurring; }

    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    @JsonProperty("isActive")
    public boolean isActive() { return isActive; }
    @JsonProperty("isActive")
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(LocalDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
}
