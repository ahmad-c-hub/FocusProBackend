package com.example.focuspro.services;

import com.example.focuspro.entities.LockInSession;
import com.example.focuspro.repos.ActivityLogRepo;
import com.example.focuspro.repos.DailyAppUsageRepo;
import com.example.focuspro.repos.GameResultRepo;
import com.example.focuspro.repos.LockInSessionRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Computes real usage statistics for the profile and home pages.
 *
 *   games_played          – total game sessions (rows) the user has ever played
 *   focus_minutes         – game session time + completed lock-in session time
 *   books_explored        – count of AI_RETENTION_TEST_SUBMITTED activity-log entries (no cap)
 *   distracting_minutes   – total minutes on tracked distracting apps today
 */
@Service
public class UserStatsService {

    @Autowired private GameResultRepo       gameResultRepo;
    @Autowired private ActivityLogRepo      activityLogRepo;
    @Autowired private LockInSessionRepo    lockInSessionRepo;
    @Autowired private DailyAppUsageRepo    dailyAppUsageRepo;
    @Autowired private ScreenPenaltyService screenPenaltyService;

    public record UserStats(
            int gamesPlayed,
            int focusMinutes,
            int booksExplored,
            int distractingMinutes) {}

    public UserStats getStats(int userId) {

        // ── Games played ──────────────────────────────────────────────────────
        // Count every session row — not just distinct game types.
        int gamesPlayed = gameResultRepo.countAllByUserId(userId);

        // ── Focus time (minutes) ──────────────────────────────────────────────
        // 1. Seconds from every game session
        int gameSeconds = gameResultRepo.findByUserIdOrderByPlayedAtDesc(userId)
                .stream()
                .mapToInt(r -> r.getTimePlayedSeconds())
                .sum();

        // 2. Duration of every completed lock-in session (startedAt → endedAt)
        List<LockInSession> completedSessions =
                lockInSessionRepo.findByUserIdAndEndedAtIsNotNull(userId);
        long lockInSeconds = completedSessions.stream()
                .mapToLong(s -> Duration.between(s.getStartedAt(), s.getEndedAt()).getSeconds())
                .sum();

        int focusMinutes = (int) ((gameSeconds + lockInSeconds) / 60);

        // ── Books explored ────────────────────────────────────────────────────
        // Real count — no 20-entry cap.
        int booksExplored = activityLogRepo
                .countByUserIdAndActivityType(userId, "AI_RETENTION_TEST_SUBMITTED");

        // ── Distracting minutes (today) ───────────────────────────────────────
        // Sum of all tracked distracting-app minutes from today's usage rows.
        int distractingMinutes = screenPenaltyService.totalDistractingMinutes(
                dailyAppUsageRepo.findByUserIdAndUsageDateOrderByTotalMinutesDesc(
                        userId, LocalDate.now()));

        return new UserStats(gamesPlayed, focusMinutes, booksExplored, distractingMinutes);
    }

    public UserStats getTodayStats(int userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        // Games played today
        int gamesPlayedToday = gameResultRepo.countByUserIdToday(userId, startOfDay);

        // Focus minutes today = game time + completed lock-in session time
        int gameSecondsToday = gameResultRepo.sumTimePlayedSecondsByUserIdToday(userId, startOfDay);

        long lockInSecondsToday = lockInSessionRepo
                .findByUserIdAndSessionDate(userId, LocalDate.now())
                .stream()
                .filter(s -> s.getEndedAt() != null)
                .mapToLong(s -> Duration.between(s.getStartedAt(), s.getEndedAt()).getSeconds())
                .sum();

        int focusMinutesToday = (int) ((gameSecondsToday + lockInSecondsToday) / 60);

        // Snippets explored today — count passed comprehension quizzes
        int snippetsToday = activityLogRepo.countByUserIdAndActivityTypeToday(
                userId, "AI_COMPREHENSION_PASSED", startOfDay);

        return new UserStats(gamesPlayedToday, focusMinutesToday, snippetsToday, 0);
    }
}
