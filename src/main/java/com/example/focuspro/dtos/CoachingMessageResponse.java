package com.example.focuspro.dtos;

import java.util.List;
import java.util.Map;

public class CoachingMessageResponse {

    private String reply;
    private long sessionId;
    private List<DailyGoalDTO> updatedGoals;
    /** Full conversation history — only populated by GET /coaching/session/today */
    private List<Map<String, String>> messages;

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

    public List<Map<String, String>> getMessages() { return messages; }
    public void setMessages(List<Map<String, String>> messages) { this.messages = messages; }
}
