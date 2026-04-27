package com.example.focuspro.controllers;

import com.example.focuspro.dtos.AppScreenEventDTO;
import com.example.focuspro.dtos.AppScreenEventRequest;
import com.example.focuspro.dtos.DailyUsageRequest;
import com.example.focuspro.dtos.DailyUsageSummaryDTO;
import com.example.focuspro.services.AppScreenEventService;
import com.example.focuspro.services.DailyAppUsageService;
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

    @Autowired
    private DailyAppUsageService dailyUsageService;

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

    /**
     * POST /screen-events/daily-usage
     *
     * Flutter sends today's aggregated app usage totals (from UsageStatsManager)
     * every ~10 minutes. One row per app per day — upserted on the server.
     */
    @PostMapping("/daily-usage")
    public ResponseEntity<Map<String, Integer>> saveDailyUsage(
            @RequestBody DailyUsageRequest request) {
        int saved = dailyUsageService.upsertDailyUsage(request);
        return ResponseEntity.ok(Map.of("saved", saved));
    }

    /**
     * GET /screen-events/summary?date=yyyy-MM-dd
     *
     * Returns today's screen-time totals per app for the current user,
     * sorted by most-used first. Accepts an optional "date" query param
     * (device local date) to avoid server-timezone vs device-timezone mismatch.
     */
    @GetMapping("/summary")
    public ResponseEntity<List<DailyUsageSummaryDTO>> getSummary(
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(dailyUsageService.getTodaySummary(date));
    }
}
