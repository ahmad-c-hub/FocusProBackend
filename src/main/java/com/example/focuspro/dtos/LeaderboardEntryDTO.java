package com.example.focuspro.dtos;

public class LeaderboardEntryDTO {

    private int rank;
    private String displayName;
    private String username;
    private int score;
    private boolean isCurrentUser;

    public LeaderboardEntryDTO() {}

    public LeaderboardEntryDTO(int rank, String displayName, String username,
                                int score, boolean isCurrentUser) {
        this.rank = rank;
        this.displayName = displayName;
        this.username = username;
        this.score = score;
        this.isCurrentUser = isCurrentUser;
    }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public boolean isCurrentUser() { return isCurrentUser; }
    public void setCurrentUser(boolean currentUser) { isCurrentUser = currentUser; }
}
