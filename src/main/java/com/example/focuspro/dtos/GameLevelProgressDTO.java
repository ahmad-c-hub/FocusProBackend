package com.example.focuspro.dtos;

public class GameLevelProgressDTO {

    private String gameType;
    private int maxUnlockedLevel;

    public GameLevelProgressDTO() {}

    public GameLevelProgressDTO(String gameType, int maxUnlockedLevel) {
        this.gameType = gameType;
        this.maxUnlockedLevel = maxUnlockedLevel;
    }

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }

    public int getMaxUnlockedLevel() { return maxUnlockedLevel; }
    public void setMaxUnlockedLevel(int maxUnlockedLevel) { this.maxUnlockedLevel = maxUnlockedLevel; }
}
