package com.example.focuspro.services;

import com.example.focuspro.entities.DailyGoal;
import com.example.focuspro.entities.GoalNotification;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.DailyGoalRepo;
import com.example.focuspro.repos.GoalNotificationRepo;
import com.example.focuspro.repos.UserRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_FOLLOWUPS_PER_GOAL = 2;

    @Autowired private GoalNotificationRepo notificationRepo;
    @Autowired private DailyGoalRepo goalRepo;
    @Autowired private UserRepo userRepo;
    @Autowired private AiService aiService;

    @Value("${firebase.credentials.json:}")
    private String firebaseCredentialsJson;

    private FirebaseMessaging firebaseMessaging;
    private boolean firebaseEnabled = false;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initFirebase() {
        if (firebaseCredentialsJson == null || firebaseCredentialsJson.isBlank()) {
            log.warn("FIREBASE_CREDENTIALS_JSON not configured — push notifications disabled. " +
                    "Set the env var on Render with your Firebase service account JSON.");
            return;
        }
        try {
            InputStream credStream = new ByteArrayInputStream(
                    firebaseCredentialsJson.getBytes(StandardCharsets.UTF_8));
            GoogleCredentials credentials = GoogleCredentials.fromStream(credStream);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            firebaseMessaging = FirebaseMessaging.getInstance();
            firebaseEnabled = true;
            log.info("Firebase initialized successfully — push notifications enabled.");
        } catch (Exception e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
        }
    }

    // ── Called by CoachingService when goals are set ──────────────────────────

    public void scheduleNotificationsForGoals(List<DailyGoal> goals, Users user) {
        for (DailyGoal goal : goals) {
            try {
                scheduleForGoal(goal, user);
            } catch (Exception e) {
                log.warn("Could not schedule notifications for goal {}: {}", goal.getId(), e.getMessage());
            }
        }
    }

    private void scheduleForGoal(DailyGoal goal, Users user) {
        // Clear any pending unsent notifications for this goal (in case of re-scheduling)
        notificationRepo.deleteByGoalIdAndSentFalse(goal.getId());

        LocalDateTime now = LocalDateTime.now();
        String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"));
        String dayStr  = now.getDayOfWeek().name();

        String systemPrompt = """
                You are a smart notification scheduler for a productivity coach app called FocusPro.
                You decide exactly when to send push notifications for a user's personal goal.
                Return ONLY valid JSON. No markdown, no explanation, no code blocks.
                """;

        String userPrompt = String.format("""
                User goal: "%s"
                Current time: %s (%s)

                Plan push notifications for this goal. Follow these rules exactly:
                - If the goal mentions a specific time (e.g. "at 3pm", "tonight at 8", "this morning"):
                  * Schedule one REMINDER ~25 minutes BEFORE that time (to help them prepare)
                  * Schedule one CHECKIN ~45 minutes AFTER that time (to see if they finished)
                - If no time is mentioned:
                  * Schedule one MOTIVATION at a sensible time today
                  * Schedule one CHECKIN later in the day
                - minutesFromNow must be >= 10 (no past or immediate notifications)
                - If a notification would be in the past (negative or < 10), omit it entirely
                - Messages must be personal, conversational, and reference the specific goal. Max 80 characters.
                - Maximum 2 notifications total.

                Return this exact JSON format:
                {
                  "notifications": [
                    {
                      "minutesFromNow": 25,
                      "type": "REMINDER",
                      "title": "Almost time!",
                      "message": "Your gym session starts in 25 minutes. Get ready!"
                    }
                  ]
                }
                """, goal.getGoalText(), timeStr, dayStr);

        String aiResponse = aiService.callAiApiPublic(systemPrompt, userPrompt);
        parseAndSaveSchedule(aiResponse, goal, user.getId());
    }

    private void parseAndSaveSchedule(String aiJson, DailyGoal goal, int userId) {
        try {
            String cleaned = aiJson.trim()
                    .replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode notifs = root.get("notifications");
            if (notifs == null || !notifs.isArray() || notifs.isEmpty()) {
                log.warn("AI returned no notifications for goal {}", goal.getId());
                return;
            }

            for (JsonNode n : notifs) {
                int minutesFromNow = n.path("minutesFromNow").asInt(0);
                if (minutesFromNow < 10) continue;

                String typeStr = n.path("type").asText("CHECKIN");
                GoalNotification.Type type;
                try {
                    type = GoalNotification.Type.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    type = GoalNotification.Type.CHECKIN;
                }

                GoalNotification notif = new GoalNotification();
                notif.setUserId(userId);
                notif.setGoalId(goal.getId());
                notif.setGoalText(goal.getGoalText());
                notif.setNotificationType(type);
                notif.setTitle(n.path("title").asText("FocusPro"));
                notif.setMessage(n.path("message").asText("Time to check in on your goal!"));
                notif.setScheduledAt(LocalDateTime.now().plusMinutes(minutesFromNow));
                notif.setSent(false);
                notificationRepo.save(notif);

                log.info("Scheduled {} notification for goal '{}' in {} minutes",
                        type, goal.getGoalText(), minutesFromNow);
            }
        } catch (Exception e) {
            log.error("Failed to parse AI notification schedule: {}", e.getMessage());
        }
    }

    // ── Scheduled task: runs every 60 seconds ─────────────────────────────────

    @Scheduled(fixedDelay = 60_000)
    public void sendPendingNotifications() {
        List<GoalNotification> pending = notificationRepo
                .findByScheduledAtBeforeAndSentFalse(LocalDateTime.now());

        for (GoalNotification notif : pending) {
            try {
                processPendingNotification(notif);
            } catch (Exception e) {
                log.error("Error processing notification {}: {}", notif.getId(), e.getMessage());
            }
        }
    }

    private void processPendingNotification(GoalNotification notif) throws Exception {
        Users user = userRepo.findById(notif.getUserId()).orElse(null);
        if (user == null) {
            markSent(notif);
            return;
        }

        DailyGoal goal = goalRepo.findById(notif.getGoalId()).orElse(null);
        if (goal == null || goal.getStatus() == DailyGoal.Status.DONE
                || goal.getStatus() == DailyGoal.Status.SKIPPED) {
            markSent(notif); // goal already done/skipped — no need to notify
            return;
        }

        // Try FCM if Firebase is enabled and user has a mobile token
        if (firebaseEnabled && user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
            sendFcm(user.getFcmToken(), notif.getTitle(), notif.getMessage(),
                    Map.of(
                            "goalId", String.valueOf(notif.getGoalId()),
                            "type",   notif.getNotificationType().name(),
                            "screen", "/coaching"
                    ));
            // Schedule follow-up for mobile FCM path (web path handles this in acknowledge())
            if (notif.getNotificationType() == GoalNotification.Type.CHECKIN) {
                long pendingFollowups = notificationRepo.countPendingFollowupsForGoal(notif.getGoalId());
                if (pendingFollowups < MAX_FOLLOWUPS_PER_GOAL) {
                    scheduleAiFollowup(goal, user);
                }
            }
            markSent(notif);
        }
        // If no FCM token (web users): leave unsent so Flutter web polls /notifications/pending
    }

    private void scheduleAiFollowup(DailyGoal goal, Users user) {
        String systemPrompt = """
                You are a caring productivity coach sending a follow-up push notification.
                The user has not yet completed their goal.
                Return ONLY valid JSON. No markdown.
                """;
        String userPrompt = String.format("""
                The user hasn't finished: "%s"
                Write a warm, encouraging follow-up push notification (not nagging).
                Reference the specific goal. Keep message under 80 characters.

                Return JSON: {"title": "...", "message": "..."}
                """, goal.getGoalText());

        try {
            String aiJson = aiService.callAiApiPublic(systemPrompt, userPrompt);
            String cleaned = aiJson.trim().replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);

            GoalNotification followup = new GoalNotification();
            followup.setUserId(user.getId());
            followup.setGoalId(goal.getId());
            followup.setGoalText(goal.getGoalText());
            followup.setNotificationType(GoalNotification.Type.FOLLOWUP);
            followup.setTitle(root.path("title").asText("Still working on it?"));
            followup.setMessage(root.path("message").asText("How's your goal going?"));
            followup.setScheduledAt(LocalDateTime.now().plusMinutes(45));
            followup.setSent(false);
            notificationRepo.save(followup);

            log.info("AI scheduled FOLLOWUP for goal '{}' in 45 minutes", goal.getGoalText());
        } catch (Exception e) {
            log.warn("Failed to schedule AI follow-up for goal {}: {}", goal.getId(), e.getMessage());
        }
    }

    // ── FCM send ──────────────────────────────────────────────────────────────

    private void sendFcm(String token, String title, String body, Map<String, String> data)
            throws Exception {
        Message msg = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .build();
        String msgId = firebaseMessaging.send(msg);
        log.info("FCM sent: {}", msgId);
    }

    // ── Save FCM token from Flutter app ──────────────────────────────────────

    public void saveFcmToken(int userId, String token) {
        userRepo.findById(userId).ifPresent(user -> {
            user.setFcmToken(token);
            userRepo.save(user);
            log.info("FCM token saved for user {}", userId);
        });
    }

    // ── Web polling: return due unsent notifications for this user ────────────

    public List<Map<String, Object>> getPendingForUser(int userId) {
        return notificationRepo.findByScheduledAtBeforeAndSentFalse(LocalDateTime.now())
                .stream()
                .filter(n -> n.getUserId() == userId)
                .map(n -> {
                    // Skip if goal is already done/skipped
                    DailyGoal goal = goalRepo.findById(n.getGoalId()).orElse(null);
                    if (goal == null || goal.getStatus() == DailyGoal.Status.DONE
                            || goal.getStatus() == DailyGoal.Status.SKIPPED) {
                        markSent(n);
                        return null;
                    }
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id",      n.getId());
                    m.put("title",   n.getTitle());
                    m.put("message", n.getMessage());
                    m.put("type",    n.getNotificationType().name());
                    m.put("goalId",  n.getGoalId());
                    return m;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // ── Acknowledge: Flutter web calls this after showing the notification ────

    public void acknowledge(long notificationId, int userId) {
        notificationRepo.findById(notificationId).ifPresent(n -> {
            if (n.getUserId() != userId) return;

            // Schedule AI follow-up if this was a CHECKIN
            if (n.getNotificationType() == GoalNotification.Type.CHECKIN) {
                DailyGoal goal = goalRepo.findById(n.getGoalId()).orElse(null);
                Users user = userRepo.findById(userId).orElse(null);
                if (goal != null && user != null
                        && goal.getStatus() != DailyGoal.Status.DONE
                        && goal.getStatus() != DailyGoal.Status.SKIPPED) {
                    long pendingFollowups = notificationRepo.countPendingFollowupsForGoal(n.getGoalId());
                    if (pendingFollowups < MAX_FOLLOWUPS_PER_GOAL) {
                        scheduleAiFollowup(goal, user);
                    }
                }
            }
            markSent(n);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void markSent(GoalNotification notif) {
        notif.setSent(true);
        notif.setSentAt(LocalDateTime.now());
        notificationRepo.save(notif);
    }
}
