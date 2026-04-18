package com.example.focuspro.controllers;

import com.example.focuspro.dtos.ManualReminderRequest;
import com.example.focuspro.entities.Users;
import com.example.focuspro.services.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired private NotificationService notificationService;

    /** Flutter registers FCM token after login (mobile). */
    @PostMapping("/fcm-token")
    public ResponseEntity<Map<String, String>> saveFcmToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "token is required"));
        notificationService.saveFcmToken(currentUser().getId(), token);
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    /** Browser fetches this to subscribe to VAPID push. No auth needed (public key is public). */
    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        String key = notificationService.getVapidPublicKey();
        if (key == null || key.isBlank())
            return ResponseEntity.ok(Map.of("key", ""));
        return ResponseEntity.ok(Map.of("key", key));
    }

    /** Browser sends its push subscription object after calling PushManager.subscribe(). */
    @PostMapping("/web-push-subscribe")
    public ResponseEntity<Map<String, String>> webPushSubscribe(@RequestBody Map<String, Object> body) {
        String endpoint = (String) body.get("endpoint");
        @SuppressWarnings("unchecked")
        Map<String, String> keys = (Map<String, String>) body.get("keys");
        if (endpoint == null || keys == null)
            return ResponseEntity.badRequest().body(Map.of("error", "endpoint and keys required"));

        notificationService.saveWebPushSubscription(
                currentUser().getId(), endpoint, keys.get("p256dh"), keys.get("auth"));
        return ResponseEntity.ok(Map.of("status", "subscribed"));
    }

    /** Polling fallback for browsers without a push subscription (tab must be open). */
    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> getPending() {
        return ResponseEntity.ok(notificationService.getPendingForUser(currentUser().getId()));
    }

    /** User sets a manual reminder at a specific time from the coaching screen. */
    @PostMapping("/reminder")
    public ResponseEntity<Map<String, String>> addReminder(@RequestBody ManualReminderRequest req) {
        if (req.getScheduledHour() < 0 || req.getScheduledHour() > 23
                || req.getScheduledMinute() < 0 || req.getScheduledMinute() > 59) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid time"));
        }
        notificationService.addManualReminder(
                currentUser().getId(),
                req.getTitle(),
                req.getMessage(),
                req.getScheduledHour(),
                req.getScheduledMinute(),
                req.getUtcOffsetMinutes());
        return ResponseEntity.ok(Map.of("status", "scheduled"));
    }

    /** Flutter web calls this after showing a polled notification so it won't repeat. */
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Map<String, String>> acknowledge(@PathVariable long id) {
        notificationService.acknowledge(id, currentUser().getId());
        return ResponseEntity.ok(Map.of("status", "acknowledged"));
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
