package com.example.focuspro.controllers;

import com.example.focuspro.dtos.DailyChallengeDTO;
import com.example.focuspro.dtos.WeaknessHintRequest;
import com.example.focuspro.services.DailyChallengeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/challenge")
public class DailyChallengeController {

    @Autowired
    private DailyChallengeService dailyChallengeService;

    /**
     * Returns today's challenge for the authenticated user.
     * If none exists (or it has expired), generates a new one via AI.
     * GET /challenge/today
     */
    @GetMapping("/today")
    public DailyChallengeDTO getTodayChallenge() {
        return dailyChallengeService.getOrGenerateTodayChallenge();
    }

    /**
     * Marks a challenge as completed.
     * POST /challenge/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public DailyChallengeDTO completeChallenge(@PathVariable Long id) {
        return dailyChallengeService.completeChallenge(id);
    }

    /**
     * Stores what the user personally feels weak at.
     * This will be used as a strong signal when generating tomorrow's challenge.
     * POST /challenge/hint
     */
    @PostMapping("/hint")
    public DailyChallengeDTO submitWeaknessHint(@RequestBody WeaknessHintRequest request) {
        return dailyChallengeService.submitWeaknessHint(request.getHint());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException ex) {
        System.err.println("[DailyChallengeController] Error: " + ex.getMessage());
        String msg = ex.getMessage() != null ? ex.getMessage() : "Challenge service error";
        if (msg.contains("AI API call failed") || msg.contains("Anthropic API call failed")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI service temporarily unavailable. Please try again.", "detail", msg));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", msg));
    }
}
