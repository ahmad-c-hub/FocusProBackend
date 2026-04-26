package com.example.focuspro.controllers;

import com.example.focuspro.dtos.AppScreenEventDTO;
import com.example.focuspro.dtos.AppScreenEventRequest;
import com.example.focuspro.services.AppScreenEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/screen-events")
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://10.0.2.2:8080",
        "https://focuspro-fm2d.onrender.com",
        "http://localhost:8080"
}, allowedHeaders = "*", methods = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.OPTIONS
})
public class AppScreenEventController {

    @Autowired
    private AppScreenEventService service;

    /**
     * POST /screen-events/batch
     *
     * Flutter sends a batch of screen-switch events captured by
     * FocusProAccessibilityService every ~30 seconds.
     * Returns how many events were saved.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Integer>> saveBatch(
            @RequestBody AppScreenEventRequest request) {
        int saved = service.saveBatch(request);
        return ResponseEntity.ok(Map.of("saved", saved));
    }

    /**
     * GET /screen-events/today
     *
     * Returns all screen events recorded for the current user today,
     * newest first. Useful for showing a timeline in the app.
     */
    @GetMapping("/today")
    public ResponseEntity<List<AppScreenEventDTO>> getToday() {
        return ResponseEntity.ok(service.getTodayEvents());
    }

    /**
     * GET /screen-events/recent
     *
     * Returns the most recent 100 events for the current user,
     * regardless of date. Useful for debugging or a full history view.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<AppScreenEventDTO>> getRecent() {
        return ResponseEntity.ok(service.getRecentEvents());
    }
}
