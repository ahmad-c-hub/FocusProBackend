package com.example.focuspro.controllers;

import com.example.focuspro.entities.Users;
import com.example.focuspro.services.DailyScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/daily-score")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com"
}, allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS
})
public class DailyScoreController {

    @Autowired
    private DailyScoreService dailyScoreService;

    /**
     * GET /daily-score/today
     * Returns the authenticated user's accumulated daily score for today.
     * Response: { "totalPoints": 12.5 }
     */
    @GetMapping("/today")
    public Map<String, Object> getToday() {
        Users user = (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        double total = dailyScoreService.getTodayScore(user.getId());
        return Map.of("totalPoints", total);
    }

    /**
     * POST /daily-score/add
     * Body: { "points": 5.0 }
     * Adds the given points to today's total and returns the new total.
     * Response: { "totalPoints": 17.5 }
     */
    @PostMapping("/add")
    public Map<String, Object> addPoints(@RequestBody Map<String, Double> body) {
        Users user = (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        double points = body.getOrDefault("points", 0.0);
        double newTotal = dailyScoreService.addPoints(user.getId(), points);
        return Map.of("totalPoints", newTotal);
    }

    /**
     * GET /daily-score/weekly
     * Returns the last 7 days of daily scores (today inclusive).
     * Response: [ { "date": "2026-04-19", "totalPoints": 8.0 }, ... ]
     */
    @GetMapping("/weekly")
    public List<DailyScoreService.DailyScoreEntry> getWeekly() {
        Users user = (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return dailyScoreService.getWeeklyScores(user.getId());
    }
}
