package com.example.focuspro.dtos;

import java.time.LocalDate;
import java.util.List;

public class DailyGameLeaderboardDTO {

    private String gameType;
    private LocalDate gameDate;
    private List<LeaderboardEntryDTO> entries;
    private LeaderboardEntryDTO currentUserEntry;

    public DailyGameLeaderboardDTO() {}

    public DailyGameLeaderboardDTO(String gameType, LocalDate gameDate,
                                    List<LeaderboardEntryDTO> entries,
                                    LeaderboardEntryDTO currentUserEntry) {
        this.gameType = gameType;
        this.gameDate = gameDate;
        this.entries = entries;
        this.currentUserEntry = currentUserEntry;
    }

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }

    public LocalDate getGameDate() { return gameDate; }
    public void setGameDate(LocalDate gameDate) { this.gameDate = gameDate; }

    public List<LeaderboardEntryDTO> getEntries() { return entries; }
    public void setEntries(List<LeaderboardEntryDTO> entries) { this.entries = entries; }

    public LeaderboardEntryDTO getCurrentUserEntry() { return currentUserEntry; }
    public void setCurrentUserEntry(LeaderboardEntryDTO currentUserEntry) { this.currentUserEntry = currentUserEntry; }
}
