package com.example.focuspro.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lock_in_sessions")
public class LockInSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "prep_ends_at", nullable = false)
    private LocalDateTime prepEndsAt;

    @Column(name = "scheduled_ends_at", nullable = false)
    private LocalDateTime scheduledEndsAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "ended_early", nullable = false)
    private boolean endedEarly;

    @Column(name = "linked_coaching_session_id")
    private Long linkedCoachingSessionId;

    public LockInSession() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }

    public LocalDate getSessionDate() { return sessionDate; }
    public void setSessionDate(LocalDate sessionDate) { this.sessionDate = sessionDate; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getPrepEndsAt() { return prepEndsAt; }
    public void setPrepEndsAt(LocalDateTime prepEndsAt) { this.prepEndsAt = prepEndsAt; }

    public LocalDateTime getScheduledEndsAt() { return scheduledEndsAt; }
    public void setScheduledEndsAt(LocalDateTime scheduledEndsAt) { this.scheduledEndsAt = scheduledEndsAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public boolean isEndedEarly() { return endedEarly; }
    public void setEndedEarly(boolean endedEarly) { this.endedEarly = endedEarly; }

    public Long getLinkedCoachingSessionId() { return linkedCoachingSessionId; }
    public void setLinkedCoachingSessionId(Long linkedCoachingSessionId) { this.linkedCoachingSessionId = linkedCoachingSessionId; }
}
