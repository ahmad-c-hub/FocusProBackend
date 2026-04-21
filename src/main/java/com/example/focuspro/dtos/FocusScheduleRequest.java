package com.example.focuspro.dtos;

public class FocusScheduleRequest {

    private String scheduleType;
    private String scheduledTime;
    private int durationMinutes;
    private int prepTimerMinutes = 5;
    private boolean isRecurring;
    private String daysOfWeek;

    public FocusScheduleRequest() {}

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
}
