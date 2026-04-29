package com.example.focuspro.dtos;

public class GameResultSubmitRequest {

    private String gameType;
    private int timePlayedSeconds;
    private boolean completed;
    private int levelReached;
    private int mistakes;
    private int correct;
    private int total;

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }

    public int getTimePlayedSeconds() { return timePlayedSeconds; }
    public void setTimePlayedSeconds(int timePlayedSeconds) { this.timePlayedSeconds = timePlayedSeconds; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public int getLevelReached() { return levelReached; }
    public void setLevelReached(int levelReached) { this.levelReached = levelReached; }

    public int getMistakes() { return mistakes; }
    public void setMistakes(int mistakes) { this.mistakes = mistakes; }

    public int getCorrect() { return correct; }
    public void setCorrect(int correct) { this.correct = correct; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
}
