package com.example.focuspro.dtos;

import java.time.LocalDateTime;

/** Read-back DTO — returned when the client fetches stored screen events. */
public class AppScreenEventDTO {

    private Long id;
    private String packageName;
    private String appName;
    private String activityName;
    private LocalDateTime startedAt;
    private LocalDateTime recordedAt;

    public AppScreenEventDTO() {}

    public AppScreenEventDTO(Long id, String packageName, String appName,
                              String activityName, LocalDateTime startedAt,
                              LocalDateTime recordedAt) {
        this.id = id;
        this.packageName = packageName;
        this.appName = appName;
        this.activityName = activityName;
        this.startedAt = startedAt;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public String getActivityName() { return activityName; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
