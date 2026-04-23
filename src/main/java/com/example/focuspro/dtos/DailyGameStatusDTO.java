package com.example.focuspro.dtos;

import java.time.LocalDate;

public class DailyGameStatusDTO {

    private String gameType;
    private String gameTitle;
    private String gameDescription;
    private LocalDate gameDate;
    private boolean hasPlayed;
    private Integer userScore;
    private Integer userRank;
    private int totalPlayers;

    public DailyGameStatusDTO() {}

    public DailyGameStatusDTO(String gameType, String gameTitle, String gameDescription,
                               LocalDate gameDate, boolean hasPlayed, Integer userScore,
                               Integer userRank, int totalPlayers) {
        this.gameType = gameType;
        this.gameTitle = gameTitle;
        this.gameDescription = gameDescription;
        this.gameDate = gameDate;
        this.hasPlayed = hasPlayed;
        this.userScore = userScore;
        this.userRank = userRank;
        this.totalPlayers = totalPlayers;
    }

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }

    public String getGameTitle() { return gameTitle; }
    public void setGameTitle(String gameTitle) { this.gameTitle = gameTitle; }

    public String getGameDescription() { return gameDescription; }
    public void setGameDescription(String gameDescription) { this.gameDescription = gameDescription; }

    public LocalDate getGameDate() { return gameDate; }
    public void setGameDate(LocalDate gameDate) { this.gameDate = gameDate; }

    public boolean isHasPlayed() { return hasPlayed; }
    public void setHasPlayed(boolean hasPlayed) { this.hasPlayed = hasPlayed; }

    public Integer getUserScore() { return userScore; }
    public void setUserScore(Integer userScore) { this.userScore = userScore; }

    public Integer getUserRank() { return userRank; }
    public void setUserRank(Integer userRank) { this.userRank = userRank; }

    public int getTotalPlayers() { return totalPlayers; }
    public void setTotalPlayers(int totalPlayers) { this.totalPlayers = totalPlayers; }
}
