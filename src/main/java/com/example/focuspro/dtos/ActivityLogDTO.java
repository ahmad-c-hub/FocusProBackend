package com.example.focuspro.dtos;

import java.time.LocalDateTime;

public class ActivityLogDTO {

    private long id;
    private String activityType;
    private String activityDescription;
    private String activityData;
    private LocalDateTime activityDate;

    public ActivityLogDTO(long id, String activityType, String activityDescription,
                          String activityData, LocalDateTime activityDate) {
        this.id = id;
        this.activityType = activityType;
        this.activityDescription = activityDescription;
        this.activityData = activityData;
        this.activityDate = activityDate;
    }

    public long getId() { return id; }
    public String getActivityType() { return activityType; }
    public String getActivityDescription() { return activityDescription; }
    public String getActivityData() { return activityData; }
    public LocalDateTime getActivityDate() { return activityDate; }
}
