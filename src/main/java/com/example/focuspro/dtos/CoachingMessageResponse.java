package com.example.focuspro.dtos;

import java.util.List;

public class CoachingMessageResponse {

    private String reply;
    private long sessionId;
    private List<DailyGoalDTO> updatedGoals;

    public CoachingMessageResponse() {}

    public CoachingMessageResponse(String reply, long sessionId, List<DailyGoalDTO> updatedGoals) {
        this.reply = reply;
        this.sessionId = sessionId;
        this.updatedGoals = updatedGoals;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public long getSessionId() { return sessionId; }
    public void setSessionId(long sessionId) { this.sessionId = sessionId; }

    public List<DailyGoalDTO> getUpdatedGoals() { return updatedGoals; }
    public void setUpdatedGoals(List<DailyGoalDTO> updatedGoals) { this.updatedGoals = updatedGoals; }
}
