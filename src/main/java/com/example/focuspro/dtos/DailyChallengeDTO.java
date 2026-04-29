package com.example.focuspro.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DailyChallengeDTO {

    private Long id;
    private String challengeType;
    private String targetGameType;
    private Integer targetBookId;
    private String challengeTitle;
    private String challengeDescription;
    private String weaknessArea;
    private LocalDate challengeDate;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private boolean expired;
    private boolean completed;
    private int progress;

    public DailyChallengeDTO() {}

    public DailyChallengeDTO(Long id, String challengeType, String targetGameType,
                              Integer targetBookId, String challengeTitle,
                              String challengeDescription, String weaknessArea,
                              LocalDate challengeDate, LocalDateTime completedAt,
                              LocalDateTime expiresAt, boolean expired, boolean completed,
                              int progress) {
        this.id = id;
        this.challengeType = challengeType;
        this.targetGameType = targetGameType;
        this.targetBookId = targetBookId;
        this.challengeTitle = challengeTitle;
        this.challengeDescription = challengeDescription;
        this.weaknessArea = weaknessArea;
        this.challengeDate = challengeDate;
        this.completedAt = completedAt;
        this.expiresAt = expiresAt;
        this.expired = expired;
        this.completed = completed;
        this.progress = progress;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChallengeType() { return challengeType; }
    public void setChallengeType(String challengeType) { this.challengeType = challengeType; }

    public String getTargetGameType() { return targetGameType; }
    public void setTargetGameType(String targetGameType) { this.targetGameType = targetGameType; }

    public Integer getTargetBookId() { return targetBookId; }
    public void setTargetBookId(Integer targetBookId) { this.targetBookId = targetBookId; }

    public String getChallengeTitle() { return challengeTitle; }
    public void setChallengeTitle(String challengeTitle) { this.challengeTitle = challengeTitle; }

    public String getChallengeDescription() { return challengeDescription; }
    public void setChallengeDescription(String challengeDescription) { this.challengeDescription = challengeDescription; }

    public String getWeaknessArea() { return weaknessArea; }
    public void setWeaknessArea(String weaknessArea) { this.weaknessArea = weaknessArea; }

    public LocalDate getChallengeDate() { return challengeDate; }
    public void setChallengeDate(LocalDate challengeDate) { this.challengeDate = challengeDate; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
}
