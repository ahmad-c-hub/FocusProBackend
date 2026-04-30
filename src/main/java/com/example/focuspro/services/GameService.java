package com.example.focuspro.services;

import com.example.focuspro.dtos.GameLevelProgressDTO;
import com.example.focuspro.dtos.GameResultResponse;
import com.example.focuspro.dtos.GameResultSubmitRequest;
import com.example.focuspro.entities.Game;
import com.example.focuspro.entities.GameLevelProgress;
import com.example.focuspro.entities.GameResult;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.GameLevelProgressRepo;
import com.example.focuspro.repos.GameRepo;
import com.example.focuspro.repos.GameResultRepo;
import com.example.focuspro.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GameService {

    @Autowired
    private GameRepo gameRepo;

    @Autowired
    private GameResultRepo gameResultRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private GameLevelProgressRepo gameLevelProgressRepo;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private DailyScoreService dailyScoreService;

    @Autowired
    private DailyChallengeService dailyChallengeService;

    /** Games that use the level roadmap and their max levels. */
    private static final Set<String> LEVEL_GAMES =
            Set.of("memory_matrix", "number_stream", "pattern_trail", "train_of_thought");

    private static final java.util.Map<String, Integer> MAX_LEVELS = java.util.Map.of(
            "memory_matrix",    10,
            "number_stream",    10,
            "pattern_trail",    10,
            "train_of_thought",  5
    );

    public GameResultResponse submitResult(GameResultSubmitRequest request, Users user) {
        // Use a synthetic fallback so unknown game types never crash the endpoint.
        Game game = gameRepo.findByType(request.getGameType())
                .orElseGet(() -> {
                    Game g = new Game();
                    g.setTitle(request.getGameType());
                    g.setType(request.getGameType());
                    return g;
                });

        // Unified formula: points = level(1-5) × (100 - dailyScore) / 100, rounded to 1dp.
        // Higher level → more points. Higher daily score → fewer points (natural cap at 100).
        // train_of_thought only awards points on level completion, never on game-over.
        double dailyScore = dailyScoreService.getTodayScore(user.getId());
        double focusScoreGained = (!request.isCompleted() && "train_of_thought".equals(request.getGameType()))
                ? 0.0
                : calcGamePoints(request.getLevelReached(), dailyScore);

        int score = calculateScore(request);

        GameResult result = new GameResult();
        result.setGameId(game.getId());
        result.setUserId(user.getId());
        result.setScore(score);
        result.setTimePlayedSeconds(request.getTimePlayedSeconds());
        result.setCompleted(request.isCompleted());
        result.setFocusScoreGained(focusScoreGained);
        result.setPlayedAt(LocalDateTime.now());
        gameResultRepo.save(result);

        double newFocusScore = Math.min(100.0, user.getFocusScore() + focusScoreGained);
        user.setFocusScore(newFocusScore);
        userRepo.save(user);

        // Update level progress for roadmap games — must happen before daily score
        // so a daily-score failure never blocks level persistence.
        if (LEVEL_GAMES.contains(request.getGameType()) && request.getLevelReached() > 0) {
            upsertLevelProgress(user.getId(), request.getGameType(), request.getLevelReached());
        }

        activityLogService.log(
                user.getId(),
                "GAME_PLAYED",
                buildDescription(request, game, score),
                buildJsonData(request, focusScoreGained, score)
        );

        // Auto-complete daily GAME challenge after 2 completed sessions
        if (request.isCompleted()) {
            try {
                dailyChallengeService.checkAndAutoCompleteGameChallenge(
                        user.getId(), request.getGameType());
            } catch (Exception ignored) {}
        }

        try {
            dailyScoreService.addPoints(user.getId(), focusScoreGained);
        } catch (Exception ignored) {}

        return new GameResultResponse(focusScoreGained, newFocusScore,
                "Result saved! +" + String.format("%.1f", focusScoreGained) + " focus pts");
    }

    /** Upserts max unlocked level — only ever increases, never decreases. */
    private void upsertLevelProgress(int userId, String gameType, int levelReached) {
        int maxLevel = MAX_LEVELS.getOrDefault(gameType, 1);
        int clamped  = Math.max(1, Math.min(levelReached, maxLevel));
        gameLevelProgressRepo.findByUserIdAndGameType(userId, gameType)
                .ifPresentOrElse(
                        p -> {
                            if (clamped > p.getMaxUnlockedLevel()) {
                                p.setMaxUnlockedLevel(clamped);
                                p.setUpdatedAt(LocalDateTime.now());
                                gameLevelProgressRepo.save(p);
                            }
                        },
                        () -> {
                            GameLevelProgress p = new GameLevelProgress();
                            p.setUserId(userId);
                            p.setGameType(gameType);
                            p.setMaxUnlockedLevel(clamped);
                            p.setUpdatedAt(LocalDateTime.now());
                            gameLevelProgressRepo.save(p);
                        }
                );
    }

    /** Returns all level-progress records for the user (roadmap games only). */
    public List<GameLevelProgressDTO> getLevelProgress(int userId) {
        return gameLevelProgressRepo.findByUserId(userId).stream()
                .map(p -> new GameLevelProgressDTO(p.getGameType(), p.getMaxUnlockedLevel()))
                .collect(Collectors.toList());
    }

    // ── Focus score formulas ───────────────────────────────────────────────────

    /**
     * Unified scoring formula shared by every game.
     * level    – player's current level, clamped to [1, 5].
     * dailyScore – today's accumulated score [0, 100].
     * Returns a value in [0.0, 5.0] with one decimal place, e.g. 3.7.
     */
    /**
     * Group 1 (correct/total): color_match, speed_match, train_of_thought
     * Group 2 (mistakes):      pattern_trail, number_stream, memory_matrix
     * Group 3 (time+diff):     sudoku
     */
    private static final java.util.Set<String> GROUP1 =
            java.util.Set.of("color_match", "speed_match", "train_of_thought");

    private int calculateScore(GameResultSubmitRequest req) {
        String type = req.getGameType();
        int level = req.getLevelReached();
        if (GROUP1.contains(type)) {
            double accuracy = req.getTotal() > 0 ? (double) req.getCorrect() / req.getTotal() : 0.5;
            return (int) Math.min(1000, Math.max(0, Math.round(level * 100 + accuracy * 400)));
        } else {
            // Group 2: pattern_trail, number_stream, memory_matrix
            double accuracy = Math.max(0.0, 1.0 - req.getMistakes() * 0.1);
            return (int) Math.min(1000, Math.max(0, Math.round(level * 100 + accuracy * 400)));
        }
    }

    private double calcGamePoints(int level, double dailyScore) {
        int lvl = Math.max(1, Math.min(level, 5));
        double raw = lvl * (100.0 - Math.max(0.0, Math.min(100.0, dailyScore))) / 100.0;
        return Math.round(Math.max(0.0, Math.min(5.0, raw)) * 10.0) / 10.0;
    }

    private String buildDescription(GameResultSubmitRequest req, Game game, int score) {
        return switch (req.getGameType()) {
            case "memory_matrix" ->
                    String.format("Played Memory Matrix — Score: %d, Level reached: %d",
                            score, req.getLevelReached());
            case "sudoku" ->
                    req.isCompleted()
                            ? String.format("Completed Sudoku — Time: %s, Mistakes: %d",
                                    formatTime(req.getTimePlayedSeconds()), req.getMistakes())
                            : "Started a Sudoku game";
            case "train_of_thought" ->
                    req.isCompleted()
                            ? String.format("Completed Train of Thought — Level: %d, Score: %d",
                                    req.getLevelReached(), score)
                            : String.format("Played Train of Thought — Level: %d, Score: %d",
                                    req.getLevelReached(), score);
            case "pattern_trail" ->
                    req.isCompleted()
                            ? String.format("Completed Pattern Trail — Level: %d, Score: %d",
                                    req.getLevelReached() - 1, score)
                            : String.format("Played Pattern Trail — Level: %d, Mistakes: %d",
                                    req.getLevelReached(), req.getMistakes());
            case "number_stream" ->
                    String.format("Played Number Stream — Score: %d, Level: %d, Mistakes: %d",
                            score, req.getLevelReached(), req.getMistakes());
            case "color_match" -> {
                String diffLabel = switch (req.getLevelReached()) {
                    case 1  -> "Easy";
                    case 2  -> "Medium";
                    case 3  -> "Hard";
                    default -> "Medium";
                };
                yield req.isCompleted()
                        ? String.format("Finished Color Match (%s) — Score: %d, Accuracy: %d%%",
                                diffLabel, score, accuracyPct(req, score))
                        : String.format("Played Color Match (%s) — Score: %d, Mistakes: %d",
                                diffLabel, score, req.getMistakes());
            }
            case "visual_nback" ->
                    String.format("Played Visual N-Back — Score: %d, Hits: %d, False alarms: %d",
                            score, req.getLevelReached(), req.getMistakes());
            case "go_no_go" ->
                    String.format("Played Go/No-Go — Score: %d, Inhibitions: %d, Commission errors: %d",
                            score, req.getLevelReached(), req.getMistakes());
            case "flanker_task" ->
                    String.format("Played Flanker Task — Score: %d, Correct: %d, Errors: %d",
                            score, req.getLevelReached(), req.getMistakes());
            default -> "Played " + game.getTitle();
        };
    }

    private String buildJsonData(GameResultSubmitRequest req, double gained, int score) {
        return String.format(
                "{\"gameType\":\"%s\",\"score\":%d,\"completed\":%b," +
                "\"timePlayedSeconds\":%d,\"levelReached\":%d," +
                "\"mistakes\":%d,\"focusScoreGained\":%.2f}",
                req.getGameType(), score, req.isCompleted(),
                req.getTimePlayedSeconds(), req.getLevelReached(),
                req.getMistakes(), gained
        );
    }

    private String formatTime(int totalSeconds) {
        int min = totalSeconds / 60;
        int sec = totalSeconds % 60;
        return String.format("%d:%02d", min, sec);
    }

    /** Estimates accuracy % from score (color_match: 100 pts base per correct). */
    private int accuracyPct(GameResultSubmitRequest req, int score) {
        int correct = score / 100; // rough lower-bound (ignores streak bonus)
        int total   = correct + req.getMistakes();
        if (total == 0) return 0;
        return (int) Math.round(correct * 100.0 / total);
    }
}
