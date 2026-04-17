package com.example.focuspro.controllers;

import com.example.focuspro.entities.Users;
import com.example.focuspro.services.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        notificationService.saveFcmToken(user.getId(), token);
        return ResponseEntity.ok(Map.of("status", "token saved"));
    }
}
