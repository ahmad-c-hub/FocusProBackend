package com.example.focuspro.services;

import com.example.focuspro.dtos.GameResultResponse;
import com.example.focuspro.dtos.GameResultSubmitRequest;
import com.example.focuspro.entities.Game;
import com.example.focuspro.entities.GameResult;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.GameRepo;
import com.example.focuspro.repos.GameResultRepo;
import com.example.focuspro.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class GameService {

    @Autowired
    private GameRepo gameRepo;

    @Autowired
    private GameResultRepo gameResultRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ActivityLogService activityLogService;

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

        activityLogService.log(
                user.getId(),
                "GAME_PLAYED",
                buildDescription(request, game),
                buildJsonData(request, focusScoreGained)
        );

        return new GameResultResponse(focusScoreGained, newFocusScore,
                "Result saved! +" + String.format("%.1f", focusScoreGained) + " focus pts");
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
