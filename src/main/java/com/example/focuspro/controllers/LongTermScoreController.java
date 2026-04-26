package com.example.focuspro.controllers;

import com.example.focuspro.entities.Users;
import com.example.focuspro.services.LongTermScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the long-term EMA focus score.
 *
 * GET /score/longterm
 *   Returns: { "score": 73.5, "weekTrend": 1.2 }
 *   If the diagnostic hasn't been completed: { "score": 0.0, "weekTrend": 0.0 }
 */
@RestController
@RequestMapping("/score")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com"
}, allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.OPTIONS
})
public class LongTermScoreController {

    @Autowired
    private LongTermScoreService longTermScoreService;

    @GetMapping("/longterm")
    public ResponseEntity<Map<String, Double>> getLongTermScore() {
        Users user = (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        LongTermScoreService.LongTermScoreData data = longTermScoreService.compute(user);

        return ResponseEntity.ok(Map.of(
                "score",     data.score()     != null ? data.score()     : 0.0,
                "weekTrend", data.weekTrend() != null ? data.weekTrend() : 0.0
        ));
    }
}
