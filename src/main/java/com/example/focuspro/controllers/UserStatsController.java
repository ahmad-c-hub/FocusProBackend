package com.example.focuspro.controllers;

import com.example.focuspro.entities.Users;
import com.example.focuspro.services.UserStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * GET /user/stats
 *
 * Returns real usage statistics for the currently authenticated user.
 *
 * Response:
 * {
 *   "gamesPlayed":   <int>,   // total game sessions ever played
 *   "focusMinutes":  <int>,   // total minutes spent in game sessions
 *   "booksExplored": <int>    // number of books the user has done a retention test on
 * }
 */
@RestController
@RequestMapping("/user")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com"
}, allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.OPTIONS
})
public class UserStatsController {

    @Autowired
    private UserStatsService userStatsService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Integer>> getStats() {
        Users user = (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        UserStatsService.UserStats stats = userStatsService.getStats(user.getId());

        return ResponseEntity.ok(Map.of(
                "gamesPlayed",   stats.gamesPlayed(),
                "focusMinutes",  stats.focusMinutes(),
                "booksExplored", stats.booksExplored()
        ));
    }
}
