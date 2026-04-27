package com.example.focuspro.services;

import com.example.focuspro.repos.ActivityLogRepo;
import com.example.focuspro.repos.GameResultRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Computes real usage statistics for the profile page.
 *
 * All three numbers are derived from existing DB tables — no new tables needed.
 *
 *   games_played     – count of rows in game_results for this user
 *   focus_minutes    – sum of time_played_seconds from game_results, converted to minutes
 *   books_explored   – count of distinct "AI_RETENTION_TEST_SUBMITTED" activity-log
 *                      events (one per book the user has done a retention test on)
 */
@Service
public class UserStatsService {

    @Autowired
    private GameResultRepo gameResultRepo;

    @Autowired
    private ActivityLogRepo activityLogRepo;

    public record UserStats(int gamesPlayed, int focusMinutes, int booksExplored) {}

    public UserStats getStats(int userId) {
        // ── Games played ──────────────────────────────────────────────────────
        int gamesPlayed = gameResultRepo.findByUserIdOrderByPlayedAtDesc(userId).size();

        // ── Focus time (minutes) ──────────────────────────────────────────────
        // Sum seconds from every game session the user has completed.
        int totalSeconds = gameResultRepo.findByUserIdOrderByPlayedAtDesc(userId)
                .stream()
                .mapToInt(r -> r.getTimePlayedSeconds())
                .sum();
        int focusMinutes = totalSeconds / 60;

        // ── Books explored ────────────────────────────────────────────────────
        // Each AI_RETENTION_TEST_SUBMITTED log entry represents one book session.
        // We count how many the user has submitted (regardless of pass/fail).
        int booksExplored = activityLogRepo
                .findTop20ByUserIdAndActivityTypeOrderByActivityDateDesc(userId, "AI_RETENTION_TEST_SUBMITTED")
                .size();

        return new UserStats(gamesPlayed, focusMinutes, booksExplored);
    }
}
