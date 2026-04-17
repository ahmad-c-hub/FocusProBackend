package com.example.focuspro.controllers;

import com.example.focuspro.entities.GoalNotification;
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

    @Autowired
    private NotificationService notificationService;

    /**
     * Flutter calls this after login to register (or refresh) the FCM device token.
     * Body: { "token": "<fcm_token>" }
     */
    @PostMapping("/fcm-token")
    public ResponseEntity<Map<String, String>> saveFcmToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "token is required"));
        }
        Users user = currentUser();
        notificationService.saveFcmToken(user.getId(), token);
        return ResponseEntity.ok(Map.of("status", "token saved"));
    }

    /**
     * Web polling endpoint — Flutter web calls this every minute.
     * Returns all notifications that are due (scheduled_at <= now) and not yet sent.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> getPending() {
        Users user = currentUser();
        List<Map<String, Object>> pending = notificationService.getPendingForUser(user.getId());
        return ResponseEntity.ok(pending);
    }

    /**
     * After Flutter web shows a notification in the browser, it calls this to mark it done.
     */
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Map<String, String>> acknowledge(@PathVariable long id) {
        Users user = currentUser();
        notificationService.acknowledge(id, user.getId());
        return ResponseEntity.ok(Map.of("status", "acknowledged"));
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
