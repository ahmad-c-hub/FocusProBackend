package com.example.focuspro.dtos;

import java.util.List;

public class DailyGoalRequest {

    private List<String> goals;

    public DailyGoalRequest() {}

    public List<String> getGoals() { return goals; }
    public void setGoals(List<String> goals) { this.goals = goals; }
}
