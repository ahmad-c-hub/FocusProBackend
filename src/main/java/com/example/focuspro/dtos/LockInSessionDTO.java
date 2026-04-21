package com.example.focuspro.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class LockInSessionDTO {

    private Long id;
    private Long scheduleId;
    private LocalDate sessionDate;
    private LocalDateTime startedAt;
    private LocalDateTime prepEndsAt;
    private LocalDateTime scheduledEndsAt;
    private LocalDateTime endedAt;
    private boolean endedEarly;
    private Long linkedCoachingSessionId;
    private boolean isPrepPhase;   // derived: now is before prepEndsAt and session is active
    private boolean isActive;      // derived: endedAt is null

    public LockInSessionDTO() {}

    public LockInSessionDTO(Long id, Long scheduleId, LocalDate sessionDate,
                             LocalDateTime startedAt, LocalDateTime prepEndsAt,
                             LocalDateTime scheduledEndsAt, LocalDateTime endedAt,
                             boolean endedEarly, Long linkedCoachingSessionId) {
        this.id = id;
        this.scheduleId = scheduleId;
        this.sessionDate = sessionDate;
        this.startedAt = startedAt;
        this.prepEndsAt = prepEndsAt;
        this.scheduledEndsAt = scheduledEndsAt;
        this.endedAt = endedAt;
        this.endedEarly = endedEarly;
        this.linkedCoachingSessionId = linkedCoachingSessionId;
        LocalDateTime now = LocalDateTime.now();
        this.isActive = (endedAt == null);
        this.isPrepPhase = isActive && now.isBefore(prepEndsAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public boolean isPrepPhase() { return isPrepPhase; }
    public void setPrepPhase(boolean prepPhase) { isPrepPhase = prepPhase; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
