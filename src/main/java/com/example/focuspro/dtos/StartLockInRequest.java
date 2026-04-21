package com.example.focuspro.dtos;

public class StartLockInRequest {

    private Long scheduleId;
    private int prepTimerMinutes = 5;
    private int durationMinutes = 60;

    public StartLockInRequest() {}

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }

    public int getPrepTimerMinutes() { return prepTimerMinutes; }
    public void setPrepTimerMinutes(int prepTimerMinutes) { this.prepTimerMinutes = prepTimerMinutes; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
}
