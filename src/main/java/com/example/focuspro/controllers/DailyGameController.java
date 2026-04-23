package com.example.focuspro.controllers;

import com.example.focuspro.dtos.DailyGameLeaderboardDTO;
import com.example.focuspro.dtos.DailyGameScoreSubmitRequest;
import com.example.focuspro.dtos.DailyGameStatusDTO;
import com.example.focuspro.entities.Users;
import com.example.focuspro.services.DailyGameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/daily-game")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com"
}, allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS
})
public class DailyGameController {

    @Autowired
    private DailyGameService dailyGameService;

    @GetMapping("/today")
    public DailyGameStatusDTO getTodayStatus() {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return dailyGameService.getTodayStatus(user);
    }

    @PostMapping("/submit")
    public DailyGameStatusDTO submitScore(@RequestBody DailyGameScoreSubmitRequest request) {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return dailyGameService.submitDailyScore(request, user);
    }

    @GetMapping("/leaderboard")
    public DailyGameLeaderboardDTO getLeaderboard() {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return dailyGameService.getTodayLeaderboard(user);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("Already submitted")) {
            return ResponseEntity.status(409).body(ex.getMessage());
        }
        return ResponseEntity.status(500).body(ex.getMessage());
    }
}
