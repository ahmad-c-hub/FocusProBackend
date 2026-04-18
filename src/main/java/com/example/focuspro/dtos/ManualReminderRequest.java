package com.example.focuspro.dtos;

public class ManualReminderRequest {
    private String title;
    private String message;
    private int scheduledHour;
    private int scheduledMinute;
    private int utcOffsetMinutes = 0;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getScheduledHour() { return scheduledHour; }
    public void setScheduledHour(int scheduledHour) { this.scheduledHour = scheduledHour; }

    public int getScheduledMinute() { return scheduledMinute; }
    public void setScheduledMinute(int scheduledMinute) { this.scheduledMinute = scheduledMinute; }

    public int getUtcOffsetMinutes() { return utcOffsetMinutes; }
    public void setUtcOffsetMinutes(int utcOffsetMinutes) { this.utcOffsetMinutes = utcOffsetMinutes; }
}
