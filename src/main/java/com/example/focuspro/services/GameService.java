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

        double focusScoreGained = calculateFocusScoreGained(request);

        GameResult result = new GameResult();
        result.setGameId(game.getId());
        result.setUserId(user.getId());
        result.setScore(request.getScore());
        result.setTimePlayedSeconds(request.getTimePlayedSeconds());
        result.setCompleted(request.isCompleted());
        result.setFocusScoreGained(focusScoreGained);
        result.setPlayedAt(LocalDateTime.now());
        gameResultRepo.save(result);

        double currentScore = user.getFocusScore() != null ? user.getFocusScore() : 0.0;
        double newFocusScore = Math.min(100.0, currentScore + focusScoreGained);
        user.setFocusScore(newFocusScore);
        userRepo.save(user);

        // Update level progress for roadmap games
        if (LEVEL_GAMES.contains(request.getGameType()) && request.getLevelReached() > 0) {
            upsertLevelProgress(user.getId(), request.getGameType(), request.getLevelReached());
        }

        activityLogService.log(
                user.getId(),
                "GAME_PLAYED",
                buildDescription(request, game),
                buildJsonData(request, focusScoreGained)
        );

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

    private double calculateFocusScoreGained(GameResultSubmitRequest req) {
        return switch (req.getGameType()) {
            case "memory_matrix" ->
                // Score 100 → +1.0 pt, Score 500 → +5.0 pts (capped at 5)
                Math.min(5.0, req.getScore() / 100.0);
            case "sudoku" ->
                // Must complete puzzle to earn points; penalised per mistake
                req.isCompleted()
                        ? Math.max(0.5, 3.0 - (req.getMistakes() * 0.5))
                        : 0.0;
            case "train_of_thought" ->
                // Each correct train routed = 0.5 pts, capped at 5
                Math.min(5.0, (req.getScore() / 100.0) * 0.5);
            case "number_stream" ->
                // Score-based, capped at 4 pts
                Math.min(4.0, req.getScore() / 150.0);
            case "color_match" ->
                // 100 pts per correct answer + streak bonus → 1000 pts ≈ +5 focus pts (capped)
                Math.min(5.0, req.getScore() / 200.0);
            case "visual_nback" ->
                Math.min(5.0, req.getScore() / 40.0);
            case "go_no_go" ->
                Math.min(4.0, req.getScore() / 60.0);
            case "flanker_task" ->
                req.isCompleted()
                        ? Math.min(5.0, req.getScore() / 60.0)
                        : Math.min(2.0, req.getScore() / 60.0);
            default -> 0.0;
        };
    }

    private String buildDescription(GameResultSubmitRequest req, Game game) {
        return switch (req.getGameType()) {
            case "memory_matrix" ->
                    String.format("Played Memory Matrix — Score: %d, Level reached: %d",
                            req.getScore(), req.getLevelReached());
            case "sudoku" ->
                    req.isCompleted()
                            ? String.format("Completed Sudoku — Time: %s, Mistakes: %d",
                                    formatTime(req.getTimePlayedSeconds()), req.getMistakes())
                            : "Started a Sudoku game";
            case "train_of_thought" ->
                    req.isCompleted()
                            ? String.format("Completed Train of Thought — Level: %d, Score: %d",
                                    req.getLevelReached(), req.getScore())
                            : String.format("Played Train of Thought — Level: %d, Score: %d",
                                    req.getLevelReached(), req.getScore());
            case "number_stream" ->
                    String.format("Played Number Stream — Score: %d, Level: %d, Mistakes: %d",
                            req.getScore(), req.getLevelReached(), req.getMistakes());
            case "color_match" -> {
                String diffLabel = switch (req.getLevelReached()) {
                    case 1  -> "Easy";
                    case 2  -> "Medium";
                    case 3  -> "Hard";
                    default -> "Medium";
                };
                yield req.isCompleted()
                        ? String.format("Finished Color Match (%s) — Score: %d, Accuracy: %d%%",
                                diffLabel, req.getScore(), accuracyPct(req))
                        : String.format("Played Color Match (%s) — Score: %d, Mistakes: %d",
                                diffLabel, req.getScore(), req.getMistakes());
            }
            case "visual_nback" ->
                    String.format("Played Visual N-Back — Score: %d, Hits: %d, False alarms: %d",
                            req.getScore(), req.getLevelReached(), req.getMistakes());
            case "go_no_go" ->
                    String.format("Played Go/No-Go — Score: %d, Inhibitions: %d, Commission errors: %d",
                            req.getScore(), req.getLevelReached(), req.getMistakes());
            case "flanker_task" ->
                    String.format("Played Flanker Task — Score: %d, Correct: %d, Errors: %d",
                            req.getScore(), req.getLevelReached(), req.getMistakes());
            default -> "Played " + game.getTitle();
        };
    }

    private String buildJsonData(GameResultSubmitRequest req, double gained) {
        return String.format(
                "{\"gameType\":\"%s\",\"score\":%d,\"completed\":%b," +
                "\"timePlayedSeconds\":%d,\"levelReached\":%d," +
                "\"mistakes\":%d,\"focusScoreGained\":%.2f}",
                req.getGameType(), req.getScore(), req.isCompleted(),
                req.getTimePlayedSeconds(), req.getLevelReached(),
                req.getMistakes(), gained
        );
    }

    private String formatTime(int totalSeconds) {
        int min = totalSeconds / 60;
        int sec = totalSeconds % 60;
        return String.format("%d:%02d", min, sec);
    }

    /** Estimates accuracy % from score and mistakes (color_match: 100 pts base per correct). */
    private int accuracyPct(GameResultSubmitRequest req) {
        int correct = req.getScore() / 100; // rough lower-bound (ignores streak bonus)
        int total   = correct + req.getMistakes();
        if (total == 0) return 0;
        return (int) Math.round(correct * 100.0 / total);
    }
}
