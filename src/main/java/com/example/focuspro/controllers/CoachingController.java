package com.example.focuspro.controllers;

import com.example.focuspro.dtos.CoachingMessageRequest;
import com.example.focuspro.dtos.CoachingMessageResponse;
import com.example.focuspro.dtos.DailyGoalDTO;
import com.example.focuspro.dtos.DailyGoalRequest;
import com.example.focuspro.dtos.UpdateGoalStatusRequest;
import com.example.focuspro.services.CoachingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/coaching")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com",
        "http://localhost:8080"
}, allowedHeaders = "*", methods = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.PATCH,
        RequestMethod.OPTIONS
})
public class CoachingController {

    @Autowired
    private CoachingService coachingService;

    // POST /coaching/goals — set morning goals
    @PostMapping("/goals")
    public CoachingMessageResponse setDailyGoals(@RequestBody DailyGoalRequest req) {
        return coachingService.setDailyGoals(req.getGoals(), req.getUtcOffsetMinutes());
    }

    // POST /coaching/session/{sessionId}/message — send a coaching message
    @PostMapping("/session/{sessionId}/message")
    public CoachingMessageResponse sendMessage(@PathVariable long sessionId,
                                               @RequestBody CoachingMessageRequest req) {
        return coachingService.sendCoachingMessage(sessionId, req.getMessage());
    }

    // POST /coaching/evening — start evening check-in
    @PostMapping("/evening")
    public CoachingMessageResponse startEveningCheckin() {
        return coachingService.startEveningCheckin();
    }

    // GET /coaching/goals/today — get today's goals
    @GetMapping("/goals/today")
    public List<DailyGoalDTO> getTodayGoals() {
        return coachingService.getTodayGoals();
    }

    // GET /coaching/session/today — restore today's session after logout/login
    @GetMapping("/session/today")
    public ResponseEntity<CoachingMessageResponse> getTodaySession() {
        CoachingMessageResponse response = coachingService.getTodaySession();
        if (response == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(response);
    }

    // PATCH /coaching/goals/{goalId}/status — update goal status
    @PatchMapping("/goals/{goalId}/status")
    public DailyGoalDTO updateGoalStatus(@PathVariable long goalId,
                                          @RequestBody UpdateGoalStatusRequest req) {
        return coachingService.updateGoalStatus(goalId, req.getStatus());
    }
}
