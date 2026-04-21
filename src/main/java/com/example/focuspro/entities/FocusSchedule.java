package com.example.focuspro.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "focus_schedules")
public class FocusSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "schedule_type", nullable = false, length = 20)
    private String scheduleType; // WAKEUP or FOCUS_BLOCK

    @Column(name = "scheduled_time", nullable = false, length = 5)
    private String scheduledTime; // "HH:mm"

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "prep_timer_minutes", nullable = false)
    private int prepTimerMinutes;

    @Column(name = "is_recurring", nullable = false)
    private boolean isRecurring;

    @Column(name = "days_of_week", length = 30)
    private String daysOfWeek; // nullable — "MON,TUE,WED"

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    public FocusSchedule() {}

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

    public boolean isRecurring() { return isRecurring; }
    public void setRecurring(boolean recurring) { isRecurring = recurring; }

    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(LocalDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
}
