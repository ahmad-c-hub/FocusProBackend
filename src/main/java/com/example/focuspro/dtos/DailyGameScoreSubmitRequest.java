package com.example.focuspro.dtos;

public class DailyGameScoreSubmitRequest {

    private int score;
    private int timePlayedSeconds;
    private int levelReached;
    private int mistakes;
    private boolean completed;

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getTimePlayedSeconds() { return timePlayedSeconds; }
    public void setTimePlayedSeconds(int timePlayedSeconds) { this.timePlayedSeconds = timePlayedSeconds; }

    public int getLevelReached() { return levelReached; }
    public void setLevelReached(int levelReached) { this.levelReached = levelReached; }

    public int getMistakes() { return mistakes; }
    public void setMistakes(int mistakes) { this.mistakes = mistakes; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
