package com.example.focuspro.dtos;

import java.util.List;

public class DailyGoalRequest {

    private List<String> goals;
    private int utcOffsetMinutes = 0; // e.g. +180 for Lebanon (UTC+3)

    public DailyGoalRequest() {}

    public List<String> getGoals() { return goals; }
    public void setGoals(List<String> goals) { this.goals = goals; }

    public int getUtcOffsetMinutes() { return utcOffsetMinutes; }
    public void setUtcOffsetMinutes(int utcOffsetMinutes) { this.utcOffsetMinutes = utcOffsetMinutes; }
}
