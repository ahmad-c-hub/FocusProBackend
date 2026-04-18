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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            InputStream s = new ByteArrayInputStream(
                    firebaseCredentialsJson.getBytes(StandardCharsets.UTF_8));
            GoogleCredentials creds = GoogleCredentials.fromStream(s);
            FirebaseOptions opts = FirebaseOptions.builder().setCredentials(creds).build();
            if (FirebaseApp.getApps().isEmpty()) FirebaseApp.initializeApp(opts);
            firebaseMessaging = FirebaseMessaging.getInstance();
            firebaseEnabled = true;
            log.info("Firebase initialized.");
        } catch (Exception e) {
            log.error("Firebase init failed: {}", e.getMessage());
        }
    }

    // ── Called by CoachingService — async so it never blocks the HTTP response ──

    @Async
    public void scheduleNotificationsForGoals(List<DailyGoal> goals, Users user, int utcOffsetMinutes) {
        log.info("[Notifications] Scheduling for {} goal(s), user={}, utcOffset={}",
                goals.size(), user.getId(), utcOffsetMinutes);

        for (DailyGoal goal : goals) {
            try {
                Thread.sleep(1500); // space out Groq calls to avoid rate limiting
                scheduleForGoal(goal, user.getId(), utcOffsetMinutes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[Notifications] Unexpected error for goal '{}': {}",
                        goal.getGoalText(), e.getMessage(), e);
            }
        }
        log.info("[Notifications] Done scheduling for user={}", user.getId());
    }

    private void scheduleForGoal(DailyGoal goal, int userId, int utcOffsetMinutes) {
        log.info("[Notifications] Processing goal id={} '{}'", goal.getId(), goal.getGoalText());
        notificationRepo.deleteByGoalIdAndSentFalse(goal.getId());

        // User's current local time (server is UTC on Render)
        LocalDateTime userNow = LocalDateTime.now().plusMinutes(utcOffsetMinutes);

        // 1. Try AI scheduling
        List<GoalNotification> scheduled = null;
        try {
            scheduled = scheduleWithAi(goal, userId, userNow);
        } catch (Exception e) {
            log.warn("[Notifications] AI scheduling failed for '{}', using fallback. Error: {}",
                    goal.getGoalText(), e.getMessage());
        }

        // 2. Fallback: rule-based scheduling (works even when AI is down)
        if (scheduled == null || scheduled.isEmpty()) {
            log.info("[Notifications] Using rule-based fallback for '{}'", goal.getGoalText());
            scheduled = scheduleWithRules(goal, userId, userNow);
        }

        // 3. Persist
        for (GoalNotification n : scheduled) {
            notificationRepo.save(n);
            log.info("[Notifications] Saved {} notification at {} for goal '{}'",
                    n.getNotificationType(), n.getScheduledAt(), goal.getGoalText());
        }
    }

    // ── AI-based scheduling ───────────────────────────────────────────────────

    private List<GoalNotification> scheduleWithAi(DailyGoal goal, int userId, LocalDateTime userNow) {
        String timeStr = userNow.format(DateTimeFormatter.ofPattern("HH:mm"));
        String dayStr  = userNow.getDayOfWeek().name();

        String systemPrompt = """
                You are a smart notification scheduler for FocusPro.
                Return ONLY valid JSON. No markdown, no explanation.
                """;
        String userPrompt = String.format("""
                User goal: "%s"
                User's current local time: %s (%s)

                Plan push notifications:
                - If a specific time is mentioned: REMINDER 25 min before, CHECKIN 45 min after.
                - If no time: one MOTIVATION now+reasonable time, one CHECKIN later.
                - minutesFromNow must be >= 10. Skip any that would be negative.
                - Message must reference the specific goal. Max 80 chars.
                - Max 2 notifications.

                JSON only:
                {"notifications":[{"minutesFromNow":25,"type":"REMINDER","title":"...","message":"..."}]}
                """, goal.getGoalText(), timeStr, dayStr);

        String raw = aiService.callAiApiPublic(systemPrompt, userPrompt);
        if (raw == null || raw.isBlank()) return List.of();

        String cleaned = raw.trim().replaceAll("```json", "").replaceAll("```", "").trim();
        JsonNode root = null;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("[Notifications] AI returned non-JSON: {}", raw.substring(0, Math.min(200, raw.length())));
            return List.of();
        }

        JsonNode notifs = root.get("notifications");
        if (notifs == null || !notifs.isArray()) return List.of();

        List<GoalNotification> result = new ArrayList<>();
        for (JsonNode n : notifs) {
            int mins = n.path("minutesFromNow").asInt(0);
            if (mins < 10) continue;
            if (mins > 720) {
                log.warn("[Notifications] AI returned unreasonably large minutesFromNow={} — skipping", mins);
                continue;
            }
            String typeStr = n.path("type").asText("CHECKIN");
            GoalNotification.Type type;
            try { type = GoalNotification.Type.valueOf(typeStr); }
            catch (IllegalArgumentException e) { type = GoalNotification.Type.CHECKIN; }

            result.add(build(userId, goal, type,
                    n.path("title").asText("FocusPro"),
                    n.path("message").asText("Time to check on your goal!"),
                    mins));
        }
        return result;
    }

    // ── Rule-based fallback (no AI needed) ───────────────────────────────────

    private List<GoalNotification> scheduleWithRules(DailyGoal goal, int userId, LocalDateTime userNow) {
        List<GoalNotification> result = new ArrayList<>();
        String text = goal.getGoalText();
        String shortText = text.length() > 45 ? text.substring(0, 42) + "…" : text;

        Integer goalMins = extractGoalMinutesFromNow(text, userNow);

        if (goalMins != null) {
            // Goal has a specific time
            if (goalMins > 30) {
                result.add(build(userId, goal, GoalNotification.Type.REMINDER,
                        "Starting soon!",
                        "\"" + shortText + "\" starts in ~25 minutes. Get ready!",
                        Math.max(10, goalMins - 25)));
            }
            result.add(build(userId, goal, GoalNotification.Type.CHECKIN,
                    "Goal check-in",
                    "Did you finish \"" + shortText + "\"? Mark it done!",
                    goalMins + 45));
        } else {
            // No specific time — check in after 2 hours
            result.add(build(userId, goal, GoalNotification.Type.CHECKIN,
                    "Goal check-in",
                    "How's your \"" + shortText + "\" going?",
                    120));
        }
        return result;
    }

    /**
     * Tries to parse a clock time from the goal text (e.g. "at 3pm", "at 22:44", "tonight at 9").
     * Returns minutes from userNow to that time, or null if no time found.
     */
    private Integer extractGoalMinutesFromNow(String goalText, LocalDateTime userNow) {
        // Matches: "at 3pm", "at 10:30 AM", "at 22:44", "at 9", etc.
        Pattern p = Pattern.compile(
                "(?i)at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?(?!\\s*\\d)");
        Matcher m = p.matcher(goalText);
        if (!m.find()) return null;

        int hour   = Integer.parseInt(m.group(1));
        int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        String ampm = m.group(3);

        if (ampm != null) {
            if (ampm.equalsIgnoreCase("pm") && hour < 12) hour += 12;
            if (ampm.equalsIgnoreCase("am") && hour == 12) hour = 0;
        } else if (hour < 7 && goalText.toLowerCase().contains("night")) {
            hour += 12; // "tonight at 9" → 21:00
        }

        LocalDateTime goalTime = userNow.toLocalDate().atTime(LocalTime.of(hour, minute));
        if (goalTime.isBefore(userNow.minusMinutes(5))) {
            goalTime = goalTime.plusDays(1); // goal is tomorrow
        }

        long mins = Duration.between(userNow, goalTime).toMinutes();
        return mins < 10 ? 10 : (int) mins;
    }

    // ── Scheduled delivery: fires every 60 seconds ────────────────────────────

    @Scheduled(fixedDelay = 60_000)
    public void sendPendingNotifications() {
        List<GoalNotification> pending =
                notificationRepo.findByScheduledAtBeforeAndSentFalse(LocalDateTime.now());

        for (GoalNotification notif : pending) {
            try {
                deliver(notif);
            } catch (Exception e) {
                log.error("[Notifications] Delivery error for id={}: {}", notif.getId(), e.getMessage());
            }
        }
    }

    private void deliver(GoalNotification notif) throws Exception {
        Users user = userRepo.findById(notif.getUserId()).orElse(null);
        if (user == null) { markSent(notif); return; }

        // For goal-linked notifications: skip if goal is already done/skipped
        if (notif.getGoalId() != null) {
            DailyGoal goal = goalRepo.findById(notif.getGoalId()).orElse(null);
            if (goal == null
                    || goal.getStatus() == DailyGoal.Status.DONE
                    || goal.getStatus() == DailyGoal.Status.SKIPPED) {
                markSent(notif);
                return;
            }
        }

        boolean delivered = false;

        // Priority 1: VAPID Web Push (background even when tab closed).
        // Only marks delivered=true if the user actually has a registered subscription;
        // otherwise falls through to polling so the notification isn't silently dropped.
        if (webPushService.isEnabled()) {
            delivered = webPushService.sendToUser(notif.getUserId(), notif.getTitle(), notif.getMessage());
        }

        // Priority 2: FCM (native mobile app)
        if (!delivered && firebaseEnabled
                && user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
            sendFcm(user.getFcmToken(), notif.getTitle(), notif.getMessage(),
                    Map.of("goalId", String.valueOf(notif.getGoalId()),
                           "type",   notif.getNotificationType().name()));
            delivered = true;
        }

        // Priority 3: leave pending — Flutter web polling will show it while tab is open
        if (!delivered) return;

        // Schedule AI follow-up if this was a CHECKIN on a goal-linked notification
        if (notif.getNotificationType() == GoalNotification.Type.CHECKIN
                && notif.getGoalId() != null) {
            DailyGoal goal = goalRepo.findById(notif.getGoalId()).orElse(null);
            if (goal != null
                    && notificationRepo.countPendingFollowupsForGoal(notif.getGoalId()) < MAX_FOLLOWUPS_PER_GOAL) {
                scheduleFollowup(goal, user);
            }
        }
        markSent(notif);
    }

    private void scheduleFollowup(DailyGoal goal, Users user) {
        // Try AI message, fall back to template
        String title   = "Still on it?";
        String message = "You haven't finished \"" +
                (goal.getGoalText().length() > 45
                        ? goal.getGoalText().substring(0, 42) + "…"
                        : goal.getGoalText()) + "\" yet. You've got this!";
        try {
            Thread.sleep(2000);
            String aiJson = aiService.callAiApiPublic(
                    "Return ONLY valid JSON. No markdown.",
                    "Write a warm, encouraging follow-up notification for a user who hasn't finished: \""
                            + goal.getGoalText() + "\". Max 80 chars. JSON: {\"title\":\"...\",\"message\":\"...\"}");
            String cleaned = aiJson.trim().replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);
            title   = root.path("title").asText(title);
            message = root.path("message").asText(message);
        } catch (Exception e) {
            log.warn("[Notifications] AI follow-up failed, using template: {}", e.getMessage());
        }

        GoalNotification followup = build(user.getId(), goal, GoalNotification.Type.FOLLOWUP,
                title, message, 45);
        notificationRepo.save(followup);
        log.info("[Notifications] Follow-up scheduled for goal '{}'", goal.getGoalText());
    }

    // ── Web polling fallback ──────────────────────────────────────────────────

    public List<Map<String, Object>> getPendingForUser(int userId) {
        return notificationRepo.findByScheduledAtBeforeAndSentFalse(LocalDateTime.now())
                .stream()
                .filter(n -> n.getUserId() == userId)
                .map(n -> {
                    // For goal-linked notifications skip if goal is done/skipped
                    if (n.getGoalId() != null) {
                        DailyGoal goal = goalRepo.findById(n.getGoalId()).orElse(null);
                        if (goal == null || goal.getStatus() == DailyGoal.Status.DONE
                                || goal.getStatus() == DailyGoal.Status.SKIPPED) {
                            markSent(n); return null;
                        }
                    }
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id",      n.getId());
                    m.put("title",   n.getTitle());
                    m.put("message", n.getMessage());
                    m.put("type",    n.getNotificationType().name());
                    if (n.getGoalId() != null) m.put("goalId", n.getGoalId());
                    return m;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public void acknowledge(long notificationId, int userId) {
        notificationRepo.findById(notificationId).ifPresent(n -> {
            if (n.getUserId() != userId) return;
            if (n.getNotificationType() == GoalNotification.Type.CHECKIN
                    && n.getGoalId() != null) {
                DailyGoal goal = goalRepo.findById(n.getGoalId()).orElse(null);
                Users user = userRepo.findById(userId).orElse(null);
                if (goal != null && user != null
                        && goal.getStatus() != DailyGoal.Status.DONE
                        && goal.getStatus() != DailyGoal.Status.SKIPPED
                        && notificationRepo.countPendingFollowupsForGoal(n.getGoalId()) < MAX_FOLLOWUPS_PER_GOAL) {
                    scheduleFollowup(goal, user);
                }
            }
            markSent(n);
        });
    }

    // ── Manual reminders ─────────────────────────────────────────────────────

    public void addManualReminder(int userId, String title, String message,
                                  int scheduledHour, int scheduledMinute,
                                  int utcOffsetMinutes) {
        ZoneOffset userZone = ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60);
        LocalDateTime userNow = LocalDateTime.now(userZone);

        // Build the scheduled time in user's local timezone for today (or tomorrow)
        LocalDateTime scheduledLocal = userNow.toLocalDate()
                .atTime(scheduledHour, scheduledMinute);
        if (!scheduledLocal.isAfter(userNow.minusMinutes(1))) {
            scheduledLocal = scheduledLocal.plusDays(1); // past time → schedule for tomorrow
        }

        // Convert user local scheduled time to server UTC for storage
        long minutesFromNow = java.time.Duration.between(userNow, scheduledLocal).toMinutes();
        minutesFromNow = Math.max(1, minutesFromNow);

        GoalNotification n = new GoalNotification();
        n.setUserId(userId);
        n.setGoalId(null);
        n.setGoalText(null);
        n.setNotificationType(GoalNotification.Type.REMINDER);
        n.setTitle(title == null || title.isBlank() ? "FocusPro Reminder" : title);
        n.setMessage(message == null || message.isBlank() ? "Time to check on your goal!" : message);
        n.setScheduledAt(LocalDateTime.now().plusMinutes(minutesFromNow));
        n.setSent(false);
        notificationRepo.save(n);
        log.info("[Notifications] Manual reminder saved for user {} at {} ({}min from now)",
                userId, n.getScheduledAt(), minutesFromNow);
    }

    // ── Token management ──────────────────────────────────────────────────────

    public void saveFcmToken(int userId, String token) {
        userRepo.findById(userId).ifPresent(u -> { u.setFcmToken(token); userRepo.save(u); });
    }

    public void saveWebPushSubscription(int userId, String endpoint, String p256dh, String auth) {
        webPushService.saveSubscription(userId, endpoint, p256dh, auth);
    }

    public String getVapidPublicKey() { return webPushService.getVapidPublicKey(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GoalNotification build(int userId, DailyGoal goal, GoalNotification.Type type,
                                   String title, String message, int minutesFromNow) {
        GoalNotification n = new GoalNotification();
        n.setUserId(userId);
        n.setGoalId(goal.getId());
        n.setGoalText(goal.getGoalText());
        n.setNotificationType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setScheduledAt(LocalDateTime.now().plusMinutes(minutesFromNow));
        n.setSent(false);
        return n;
    }

    private void sendFcm(String token, String title, String body, Map<String, String> data)
            throws Exception {
        Message msg = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data).build();
        firebaseMessaging.send(msg);
    }

    private void markSent(GoalNotification notif) {
        notif.setSent(true);
        notif.setSentAt(LocalDateTime.now());
        notificationRepo.save(notif);
    }
}
