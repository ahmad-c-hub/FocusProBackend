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
        Game game = gameRepo.findByType(request.getGameType())
                .orElseThrow(() -> new IllegalArgumentException("Unknown game type: " + request.getGameType()));

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
}
