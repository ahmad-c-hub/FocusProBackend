package com.example.focuspro.dtos;

public class GameResultResponse {

    private double focusScoreGained;
    private double newFocusScore;
    private String message;

    public GameResultResponse(double focusScoreGained, double newFocusScore, String message) {
        this.focusScoreGained = focusScoreGained;
        this.newFocusScore = newFocusScore;
        this.message = message;
    }

    public double getFocusScoreGained() { return focusScoreGained; }
    public double getNewFocusScore() { return newFocusScore; }
    public String getMessage() { return message; }
}
