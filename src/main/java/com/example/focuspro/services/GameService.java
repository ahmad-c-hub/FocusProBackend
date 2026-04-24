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

        double rawGain = calculateFocusScoreGained(request);

        // Diminishing returns: the closer to 100, the less each session adds.
        // factor = ((100 - currentScore) / 100) ^ 0.6
        // At score 0 → ×1.0 | score 50 → ×0.66 | score 80 → ×0.44 | score 95 → ×0.23
        double currentScore = user.getFocusScore() != null ? user.getFocusScore() : 0.0;
        double scalingFactor = Math.pow(Math.max(0.0, (100.0 - currentScore) / 100.0), 0.6);
        double focusScoreGained = Math.round(rawGain * scalingFactor * 10.0) / 10.0;

        GameResult result = new GameResult();
        result.setGameId(game.getId());
        result.setUserId(user.getId());
        result.setScore(request.getScore());
        result.setTimePlayedSeconds(request.getTimePlayedSeconds());
        result.setCompleted(request.isCompleted());
        result.setFocusScoreGained(focusScoreGained);
        result.setPlayedAt(LocalDateTime.now());
        gameResultRepo.save(result);

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

            case "memory_matrix" -> {
                // Primary: levelReached (1-10 grid size) × 0.45 = up to 4.5 base
                // Quality: each mistake cuts 7% (floor 30%)
                double base = req.getLevelReached() * 0.45;
                double accuracy = Math.max(0.3, 1.0 - req.getMistakes() * 0.07);
                yield Math.min(5.0, base * accuracy);
            }

            case "sudoku" -> {
                // levelReached: 1=easy, 2=medium, 3=hard
                // Completed: level × 1.4 base, mistake cuts 15% (floor 40%), time bonus up to ×1.2
                // Not completed: small consolation (level × 0.3)
                int level = Math.max(1, req.getLevelReached());
                if (!req.isCompleted()) {
                    yield Math.min(1.0, level * 0.3);
                }
                double base = level * 1.4;
                double mistakeMult = Math.max(0.4, 1.0 - req.getMistakes() * 0.15);
                int targetSeconds = level == 1 ? 300 : level == 3 ? 720 : 480;
                double timeBonus = req.getTimePlayedSeconds() > 0
                        ? Math.min(1.2, (double) targetSeconds / req.getTimePlayedSeconds())
                        : 1.0;
                yield Math.min(5.0, base * mistakeMult * timeBonus);
            }

            case "speed_match" -> {
                // score encodes speed+accuracy over fixed 60s window
                // levelReached: difficulty 1=easy, 2=medium, 3=hard → multiplier
                int level = Math.max(1, req.getLevelReached());
                double diffMult = 1.0 + (level - 1) * 0.4; // 1.0 / 1.4 / 1.8
                yield Math.min(5.0, (req.getScore() / 200.0) * diffMult);
            }

            case "color_match" -> {
                // levelReached: 1=easy, 2=medium, 3=hard
                // Hard mode (30 s) vs easy (60 s) — difficulty multiplier rewards pressure
                int level = Math.max(1, req.getLevelReached());
                double diffMult = 1.0 + (level - 1) * 0.35; // 1.0 / 1.35 / 1.70
                yield Math.min(5.0, (req.getScore() / 150.0) * diffMult);
            }

            case "number_stream" -> {
                // levelReached: 1-10 roadmap level
                // Hybrid: level contribution + score contribution, mistake penalty (floor 50%)
                double levelPart = req.getLevelReached() * 0.35;
                double scorePart = req.getScore() / 300.0;
                double accuracy = Math.max(0.5, 1.0 - req.getMistakes() * 0.05);
                yield Math.min(4.0, (levelPart + scorePart) * accuracy);
            }

            case "pattern_trail" -> {
                // levelReached: 1-10 roadmap level
                // Completion matters: ×1.1 if done, ×0.7 if quit early
                double base = req.getLevelReached() * 0.45;
                double accuracy = Math.max(0.3, 1.0 - req.getMistakes() * 0.07);
                double completionMult = req.isCompleted() ? 1.1 : 0.7;
                yield Math.min(5.0, base * accuracy * completionMult);
            }

            case "train_of_thought" -> {
                // levelReached: 1-5 roadmap level
                // Time efficiency: faster than target gets bonus up to ×1.2
                int level = Math.max(1, req.getLevelReached());
                double base = level * 0.85; // level 5 → 4.25
                double completionMult = req.isCompleted() ? 1.1 : 0.6;
                int targetSeconds = level * 180;
                double timeBonus = req.getTimePlayedSeconds() > 0
                        ? Math.min(1.2, (double) targetSeconds / req.getTimePlayedSeconds())
                        : 1.0;
                yield Math.min(5.0, base * completionMult * timeBonus);
            }

            case "visual_nback" -> {
                // levelReached = hits (true positives), mistakes = false alarms
                // Signal precision: hits / (hits + falseAlarms) drives the score
                int hits = req.getLevelReached();
                int falseAlarms = req.getMistakes();
                int total = hits + falseAlarms;
                double precision = total > 0 ? (double) hits / total : 0.5;
                double scoreBonus = Math.min(0.5, req.getScore() / 200.0);
                yield Math.min(5.0, precision * 4.5 + scoreBonus);
            }

            case "go_no_go" -> {
                // levelReached = successful inhibitions (correct No-Go rejections)
                // mistakes = commission errors (pressed Go on No-Go trial)
                int inhibitions = req.getLevelReached();
                int commissionErrors = req.getMistakes();
                int total = inhibitions + commissionErrors;
                double inhibitionRate = total > 0 ? (double) inhibitions / total : 0.5;
                double scoreBonus = Math.min(0.5, req.getScore() / 400.0);
                yield Math.min(4.0, inhibitionRate * 3.5 + scoreBonus);
            }

            case "flanker_task" -> {
                // levelReached = correct responses, mistakes = errors
                // Pure accuracy score, completion multiplier
                int correct = req.getLevelReached();
                int errors = req.getMistakes();
                int total = correct + errors;
                double accuracy = total > 0 ? (double) correct / total : 0.5;
                double completionMult = req.isCompleted() ? 1.15 : 0.8;
                yield Math.min(5.0, accuracy * 4.5 * completionMult);
            }

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
