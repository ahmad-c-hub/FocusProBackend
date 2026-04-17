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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_FOLLOWUPS_PER_GOAL = 2;

    @Autowired private GoalNotificationRepo notificationRepo;
    @Autowired private DailyGoalRepo goalRepo;
    @Autowired private UserRepo userRepo;
    @Autowired private AiService aiService;
    @Autowired private WebPushService webPushService;

    @Value("${firebase.credentials.json:}")
    private String firebaseCredentialsJson;

    private FirebaseMessaging firebaseMessaging;
    private boolean firebaseEnabled = false;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initFirebase() {
        if (firebaseCredentialsJson == null || firebaseCredentialsJson.isBlank()) return;
        try {
            InputStream credStream = new ByteArrayInputStream(
                    firebaseCredentialsJson.getBytes(StandardCharsets.UTF_8));
            GoogleCredentials credentials = GoogleCredentials.fromStream(credStream);
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
            if (FirebaseApp.getApps().isEmpty()) FirebaseApp.initializeApp(options);
            firebaseMessaging = FirebaseMessaging.getInstance();
            firebaseEnabled = true;
            log.info("Firebase initialized — mobile push enabled.");
        } catch (Exception e) {
            log.error("Firebase init failed: {}", e.getMessage());
        }
    }

    // ── Called by CoachingService — runs ASYNC so it doesn't block the response ──

    @Async
    public void scheduleNotificationsForGoals(List<DailyGoal> goals, Users user, int utcOffsetMinutes) {
        for (DailyGoal goal : goals) {
            try {
                // Small delay between goals so AI calls don't burst on Groq free tier
                Thread.sleep(1500);
                scheduleForGoal(goal, user, utcOffsetMinutes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("Could not schedule notifications for goal {}: {}", goal.getId(), e.getMessage());
            }
        }
    }

    private void scheduleForGoal(DailyGoal goal, Users user, int utcOffsetMinutes) {
        notificationRepo.deleteByGoalIdAndSentFalse(goal.getId());

        // Convert server UTC to user's local time so the AI understands their goal times
        LocalDateTime userLocalNow = LocalDateTime.now().plusMinutes(utcOffsetMinutes);
        String timeStr = userLocalNow.format(DateTimeFormatter.ofPattern("HH:mm"));
        String dayStr  = userLocalNow.getDayOfWeek().name();

        String systemPrompt = """
                You are a smart notification scheduler for a productivity app called FocusPro.
                Return ONLY valid JSON. No markdown, no explanation.
                """;

        String userPrompt = String.format("""
                User goal: "%s"
                User's current local time: %s (%s)

                Plan push notifications for this goal:
                - If a specific time is mentioned (e.g. "at 3pm", "tonight at 8"):
                  * REMINDER ~25 min before that time
                  * CHECKIN ~45 min after that time
                - If no time is mentioned:
                  * MOTIVATION at a sensible time (consider current time)
                  * CHECKIN later in the day
                - minutesFromNow >= 10 only. Omit if it would be negative.
                - Messages must be personal and mention the specific goal. Max 80 chars.
                - Maximum 2 notifications.

                JSON only:
                {
                  "notifications": [
                    { "minutesFromNow": 25, "type": "REMINDER", "title": "...", "message": "..." }
                  ]
                }
                """, goal.getGoalText(), timeStr, dayStr);

        String aiResponse = aiService.callAiApiPublic(systemPrompt, userPrompt);
        parseAndSaveSchedule(aiResponse, goal, user.getId());
    }

    private void parseAndSaveSchedule(String aiJson, DailyGoal goal, int userId) {
        try {
            String cleaned = aiJson.trim().replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode notifs = root.get("notifications");
            if (notifs == null || !notifs.isArray() || notifs.isEmpty()) return;

            for (JsonNode n : notifs) {
                int minutesFromNow = n.path("minutesFromNow").asInt(0);
                if (minutesFromNow < 10) continue;

                String typeStr = n.path("type").asText("CHECKIN");
                GoalNotification.Type type;
                try { type = GoalNotification.Type.valueOf(typeStr); }
                catch (IllegalArgumentException e) { type = GoalNotification.Type.CHECKIN; }

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

                log.info("Scheduled {} in {}min for goal '{}'", type, minutesFromNow, goal.getGoalText());
            }
        } catch (Exception e) {
            log.error("Failed to parse AI notification schedule: {}", e.getMessage());
        }
    }

    // ── Scheduler: fires every 60 seconds ─────────────────────────────────────

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
        if (user == null) { markSent(notif); return; }

        DailyGoal goal = goalRepo.findById(notif.getGoalId()).orElse(null);
        if (goal == null || goal.getStatus() == DailyGoal.Status.DONE
                || goal.getStatus() == DailyGoal.Status.SKIPPED) {
            markSent(notif);
            return;
        }

        boolean delivered = false;

        // 1. Try Web Push (VAPID) — works in background on installed PWA
        if (webPushService.isEnabled()) {
            webPushService.sendToUser(notif.getUserId(), notif.getTitle(), notif.getMessage());
            delivered = true;
        }

        // 2. Try FCM (mobile app)
        if (!delivered && firebaseEnabled
                && user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
            sendFcm(user.getFcmToken(), notif.getTitle(), notif.getMessage(),
                    Map.of("goalId", String.valueOf(notif.getGoalId()),
                           "type",   notif.getNotificationType().name(),
                           "screen", "/coaching"));
            delivered = true;
        }

        // 3. Neither configured: leave pending so Flutter web polling can pick it up
        if (!delivered) return;

        if (notif.getNotificationType() == GoalNotification.Type.CHECKIN) {
            long pending = notificationRepo.countPendingFollowupsForGoal(notif.getGoalId());
            if (pending < MAX_FOLLOWUPS_PER_GOAL) {
                scheduleAiFollowup(goal, user);
            }
        }
        markSent(notif);
    }

    private void scheduleAiFollowup(DailyGoal goal, Users user) {
        try {
            Thread.sleep(2000); // avoid rate limit burst
            String aiJson = aiService.callAiApiPublic(
                "You are a caring productivity coach. Return ONLY valid JSON.",
                String.format("""
                    The user hasn't finished: "%s"
                    Write a warm, encouraging follow-up push notification.
                    Keep message under 80 chars. Reference the specific goal.
                    JSON: {"title": "...", "message": "..."}
                    """, goal.getGoalText())
            );
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
            log.info("AI scheduled FOLLOWUP for goal '{}'", goal.getGoalText());
        } catch (Exception e) {
            log.warn("Failed to schedule AI follow-up: {}", e.getMessage());
        }
    }

    private void sendFcm(String token, String title, String body, Map<String, String> data)
            throws Exception {
        Message msg = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data).build();
        firebaseMessaging.send(msg);
    }

    // ── Web polling fallback (for browsers without push subscription) ─────────

    public List<Map<String, Object>> getPendingForUser(int userId) {
        return notificationRepo.findByScheduledAtBeforeAndSentFalse(LocalDateTime.now())
                .stream()
                .filter(n -> n.getUserId() == userId)
                .map(n -> {
                    DailyGoal goal = goalRepo.findById(n.getGoalId()).orElse(null);
                    if (goal == null || goal.getStatus() == DailyGoal.Status.DONE
                            || goal.getStatus() == DailyGoal.Status.SKIPPED) {
                        markSent(n); return null;
                    }
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id",      n.getId());
                    m.put("title",   n.getTitle());
                    m.put("message", n.getMessage());
                    m.put("type",    n.getNotificationType().name());
                    m.put("goalId",  n.getGoalId());
                    return m;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public void acknowledge(long notificationId, int userId) {
        notificationRepo.findById(notificationId).ifPresent(n -> {
            if (n.getUserId() != userId) return;
            if (n.getNotificationType() == GoalNotification.Type.CHECKIN) {
                DailyGoal goal = goalRepo.findById(n.getGoalId()).orElse(null);
                Users user = userRepo.findById(userId).orElse(null);
                if (goal != null && user != null
                        && goal.getStatus() != DailyGoal.Status.DONE
                        && goal.getStatus() != DailyGoal.Status.SKIPPED
                        && notificationRepo.countPendingFollowupsForGoal(n.getGoalId()) < MAX_FOLLOWUPS_PER_GOAL) {
                    scheduleAiFollowup(goal, user);
                }
            }
            markSent(n);
        });
    }

    // ── FCM token + web push subscription management ──────────────────────────

    public void saveFcmToken(int userId, String token) {
        userRepo.findById(userId).ifPresent(user -> {
            user.setFcmToken(token);
            userRepo.save(user);
        });
    }

    public void saveWebPushSubscription(int userId, String endpoint, String p256dh, String auth) {
        webPushService.saveSubscription(userId, endpoint, p256dh, auth);
    }

    public String getVapidPublicKey() {
        return webPushService.getVapidPublicKey();
    }

    private void markSent(GoalNotification notif) {
        notif.setSent(true);
        notif.setSentAt(LocalDateTime.now());
        notificationRepo.save(notif);
    }
}
