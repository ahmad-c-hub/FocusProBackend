package com.example.focuspro.services;

import com.example.focuspro.dtos.CoachingMessageResponse;
import com.example.focuspro.dtos.DailyGoalDTO;
import com.example.focuspro.entities.ActivityLog;
import com.example.focuspro.entities.CoachingSession;
import com.example.focuspro.entities.DailyGoal;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.ActivityLogRepo;
import com.example.focuspro.repos.CoachingSessionRepo;
import com.example.focuspro.repos.DailyGoalRepo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CoachingService {

    @Autowired private DailyGoalRepo dailyGoalRepo;
    @Autowired private CoachingSessionRepo coachingSessionRepo;
    @Autowired private ActivityLogRepo activityLogRepo;
    @Autowired private ActivityLogService activityLogService;
    @Autowired private AiService aiService;
    @Autowired private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── a) Set daily goals (morning) ─────────────────────────────────────────

    public CoachingMessageResponse setDailyGoals(List<String> goalTexts, int utcOffsetMinutes) {
        Users user = currentUser();
        // Use the user's local date (not UTC server date) so goals are tagged to the right day
        ZoneOffset userZone = ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60);
        LocalDate today = LocalDate.now(userZone);

        // Delete any PENDING goals for today (allow reset)
        List<DailyGoal> pendingToday = dailyGoalRepo.findByUserIdAndGoalDateAndStatus(
                user.getId(), today, DailyGoal.Status.PENDING);
        dailyGoalRepo.deleteAll(pendingToday);

        // Create new DailyGoal rows
        List<DailyGoal> newGoals = new ArrayList<>();
        for (String text : goalTexts) {
            if (text == null || text.isBlank()) continue;
            DailyGoal goal = new DailyGoal();
            goal.setUserId(user.getId());
            goal.setGoalText(text.trim());
            goal.setStatus(DailyGoal.Status.PENDING);
            goal.setGoalDate(today);
            newGoals.add(dailyGoalRepo.save(goal));
        }

        // Open or reuse MORNING CoachingSession (using user's local date)
        CoachingSession session = coachingSessionRepo
                .findByUserIdAndSessionDateAndSessionType(user.getId(), today, CoachingSession.SessionType.MORNING)
                .orElseGet(() -> {
                    CoachingSession s = new CoachingSession();
                    s.setUserId(user.getId());
                    s.setSessionType(CoachingSession.SessionType.MORNING);
                    s.setSessionDate(today);
                    s.setGoalsSnapshot(goalsToJson(newGoals));
                    s.setConversationHistory("[]");
                    return s;
                });
        session.setGoalsSnapshot(goalsToJson(newGoals));

        // Fetch last 3 coaching activity logs for context
        String recentContext = buildRecentCoachingContext(user.getId());

        // Build goal list string
        String goalsList = buildGoalListString(newGoals);

        String systemPrompt = buildSystemPrompt(user, newGoals);

        String userPrompt = String.format("""
                This is the morning kickoff for %s. Their goals for today are:
                %s

                %s

                Give a motivating, personal, specific morning message that references their actual goals by name.
                Ask one focused question to help them prioritise. Maximum 3 sentences.
                """,
                user.getName() != null ? user.getName() : user.getUsername(),
                goalsList,
                recentContext.isEmpty() ? "" : "Recent context:\n" + recentContext
        );

        String aiReply = callAi(systemPrompt, userPrompt);

        // Save conversation history
        List<Map<String, String>> history = new ArrayList<>();
        history.add(Map.of("role", "assistant", "content", aiReply));
        session.setConversationHistory(toJson(history));
        coachingSessionRepo.save(session);

        activityLogService.log(user.getId(), "COACHING_MORNING_START",
                "Morning coaching started with " + newGoals.size() + " goals");

        // Let AI schedule smart notifications using the user's local timezone
        notificationService.scheduleNotificationsForGoals(newGoals, user, utcOffsetMinutes);

        return new CoachingMessageResponse(aiReply, session.getId(), goalsToDTOs(newGoals));
    }

    // ── b) Send coaching message ──────────────────────────────────────────────

    public CoachingMessageResponse sendCoachingMessage(long sessionId, String userMessage) {
        Users user = currentUser();

        CoachingSession session = coachingSessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (session.getUserId() != user.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        LocalDate today = LocalDate.now();
        List<DailyGoal> goals = dailyGoalRepo.findByUserIdAndGoalDate(user.getId(), today);

        // Parse existing conversation history
        List<Map<String, String>> history = parseHistory(session.getConversationHistory());

        // Append user message
        history.add(Map.of("role", "user", "content", userMessage));

        // Build system prompt
        String systemPrompt = buildSystemPrompt(user, goals);

        // Build messages for AI: system + full history
        String aiReply = callAiWithHistory(systemPrompt, history);

        // Detect goal completion signals in the user message
        goals = detectAndUpdateGoalStatus(user, goals, userMessage, aiReply);

        // Append AI reply
        history.add(Map.of("role", "assistant", "content", aiReply));
        session.setConversationHistory(toJson(history));
        coachingSessionRepo.save(session);

        activityLogService.log(user.getId(), "COACHING_MESSAGE",
                "Coaching message sent in session " + sessionId);

        return new CoachingMessageResponse(aiReply, session.getId(), goalsToDTOs(goals));
    }

    // ── c) Start evening check-in ─────────────────────────────────────────────

    public CoachingMessageResponse startEveningCheckin(int utcOffsetMinutes) {
        Users user = currentUser();
        ZoneOffset userZone = ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60);
        LocalDate today = LocalDate.now(userZone);

        List<DailyGoal> goals = dailyGoalRepo.findByUserIdAndGoalDate(user.getId(), today);

        // Build summary
        long done    = goals.stream().filter(g -> g.getStatus() == DailyGoal.Status.DONE).count();
        long skipped = goals.stream().filter(g -> g.getStatus() == DailyGoal.Status.SKIPPED).count();
        long pending = goals.stream().filter(g -> g.getStatus() == DailyGoal.Status.PENDING
                || g.getStatus() == DailyGoal.Status.IN_PROGRESS).count();

        String goalsSummary = goals.isEmpty()
                ? "No goals were set today."
                : goals.stream().map(g -> String.format("- \"%s\" → %s", g.getGoalText(), g.getStatus()))
                        .collect(Collectors.joining("\n"));

        // Find or create EVENING session (using user's local date)
        CoachingSession session = coachingSessionRepo
                .findByUserIdAndSessionDateAndSessionType(user.getId(), today, CoachingSession.SessionType.EVENING)
                .orElseGet(() -> {
                    CoachingSession s = new CoachingSession();
                    s.setUserId(user.getId());
                    s.setSessionType(CoachingSession.SessionType.EVENING);
                    s.setSessionDate(today);
                    s.setGoalsSnapshot(goalsToJson(goals));
                    s.setConversationHistory("[]");
                    return s;
                });

        String systemPrompt = buildSystemPrompt(user, goals);

        String userPrompt = String.format("""
                It's the end of the day for %s. Here is what happened with their goals today:
                %s

                Summary: %d done, %d skipped, %d still pending/in-progress.

                Give an evening reflection: what was accomplished, what wasn't, a supportive reason why that might be, and one concrete suggestion for tomorrow.
                Tone: warm, supportive, never harsh. Reference specific goals by name. Maximum 3 sentences.
                """,
                user.getName() != null ? user.getName() : user.getUsername(),
                goalsSummary, done, skipped, pending
        );

        String aiReply = callAi(systemPrompt, userPrompt);

        List<Map<String, String>> history = parseHistory(session.getConversationHistory());
        history.add(Map.of("role", "assistant", "content", aiReply));
        session.setConversationHistory(toJson(history));
        coachingSessionRepo.save(session);

        activityLogService.log(user.getId(), "COACHING_EVENING_START",
                "Evening coaching check-in started");

        return new CoachingMessageResponse(aiReply, session.getId(), goalsToDTOs(goals));
    }

    // ── d) Get today's goals ──────────────────────────────────────────────────

    public List<DailyGoalDTO> getTodayGoals() {
        Users user = currentUser();
        List<DailyGoal> goals = dailyGoalRepo.findByUserIdAndGoalDate(user.getId(), LocalDate.now());
        return goalsToDTOs(goals);
    }

    // ── d2) Get today's active session (for session restore after logout) ─────

    public CoachingMessageResponse getTodaySession() {
        Users user = currentUser();
        LocalDate today = LocalDate.now();

        Optional<CoachingSession> sessionOpt = coachingSessionRepo
                .findByUserIdAndSessionDateAndSessionType(user.getId(), today, CoachingSession.SessionType.MORNING);

        if (sessionOpt.isEmpty()) return null;

        CoachingSession session = sessionOpt.get();
        List<DailyGoal> goals = dailyGoalRepo.findByUserIdAndGoalDate(user.getId(), today);
        List<Map<String, String>> history = parseHistory(session.getConversationHistory());

        CoachingMessageResponse response = new CoachingMessageResponse();
        response.setSessionId(session.getId());
        response.setUpdatedGoals(goalsToDTOs(goals));
        response.setMessages(history);
        return response;
    }

    // ── e) Update goal status ─────────────────────────────────────────────────

    public DailyGoalDTO updateGoalStatus(long goalId, String status) {
        Users user = currentUser();
        DailyGoal goal = dailyGoalRepo.findById(goalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));

        if (goal.getUserId() != user.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        DailyGoal.Status newStatus = DailyGoal.Status.valueOf(status.toUpperCase());
        goal.setStatus(newStatus);
        if (newStatus == DailyGoal.Status.DONE && goal.getCompletedAt() == null) {
            goal.setCompletedAt(LocalDateTime.now());
        }
        dailyGoalRepo.save(goal);
        return toDTO(goal);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildSystemPrompt(Users user, List<DailyGoal> goals) {
        String name       = user.getName() != null ? user.getName() : user.getUsername();
        double focusScore = user.getFocusScore() != null ? user.getFocusScore() : 0.0;
        String goalsList  = goals.isEmpty() ? "No goals set." : buildGoalListString(goals);

        // Last 5 activity log entries
        List<ActivityLog> recentLogs = activityLogRepo
                .findByUserIdOrderByActivityDateDesc(user.getId())
                .stream().limit(5).toList();
        String logsContext = recentLogs.isEmpty()
                ? ""
                : recentLogs.stream()
                        .map(l -> l.getActivityType() + ": " + l.getActivityDescription())
                        .collect(Collectors.joining("\n"));

        return String.format("""
                You are a personal productivity coach inside FocusPro. You are direct, warm, and personal.
                You remember what the user told you. You do not give generic advice.
                You ask specific follow-up questions. You reference their actual goals by name.
                You never repeat yourself. Maximum response length: 3 sentences.

                User: %s
                Focus score: %.1f / 100
                Today's goals:
                %s

                Recent activity:
                %s
                """,
                name, focusScore, goalsList,
                logsContext.isEmpty() ? "None yet." : logsContext
        );
    }

    private String buildGoalListString(List<DailyGoal> goals) {
        if (goals.isEmpty()) return "No goals set.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < goals.size(); i++) {
            DailyGoal g = goals.get(i);
            sb.append(String.format("%d. \"%s\" [%s]\n", i + 1, g.getGoalText(), g.getStatus()));
        }
        return sb.toString().trim();
    }

    private String buildRecentCoachingContext(int userId) {
        List<ActivityLog> coachingLogs = activityLogRepo
                .findByUserIdOrderByActivityDateDesc(userId)
                .stream()
                .filter(l -> l.getActivityType() != null && l.getActivityType().startsWith("COACHING_"))
                .limit(3)
                .toList();
        if (coachingLogs.isEmpty()) return "";
        return coachingLogs.stream()
                .map(l -> l.getActivityType() + ": " + l.getActivityDescription())
                .collect(Collectors.joining("\n"));
    }

    private List<DailyGoal> detectAndUpdateGoalStatus(Users user, List<DailyGoal> goals,
                                                        String userMessage, String aiReply) {
        String combined = (userMessage + " " + aiReply).toLowerCase();
        boolean anyUpdated = false;
        for (DailyGoal goal : goals) {
            if (goal.getStatus() == DailyGoal.Status.DONE) continue;
            String goalLower = goal.getGoalText().toLowerCase();
            // Simple heuristic: if user message mentions the goal and signals completion
            if (combined.contains(goalLower) || combined.contains("goal " + (goals.indexOf(goal) + 1))) {
                if (combined.contains("finished") || combined.contains("done") ||
                    combined.contains("completed") || combined.contains("checked off") ||
                    combined.contains("wrapped up") || combined.contains("finished it") ||
                    combined.contains("did it") || combined.contains("accomplished")) {
                    goal.setStatus(DailyGoal.Status.DONE);
                    goal.setCompletedAt(LocalDateTime.now());
                    dailyGoalRepo.save(goal);
                    anyUpdated = true;
                }
            }
        }
        if (anyUpdated) {
            activityLogService.log(user.getId(), "COACHING_GOAL_UPDATED",
                    "Goal status auto-updated from conversation");
        }
        return goals;
    }

    private String callAi(String systemPrompt, String userPrompt) {
        try {
            // Use reflection to call the private callAiApi method via the public generate path
            // Since callAiApi is private, we use the existing public interface through generateSnippetQuestions
            // Instead, we'll use the same RestTemplate approach directly via AiService's exposed capability
            // The task says to call callAiApi() directly — but it's private. We'll call it via a helper
            // We need to expose it or use a workaround. Looking at AiService, callAiApi is private.
            // The cleanest solution: add a public method in AiService, or use the approach via RestTemplate directly.
            // Since the task says "call callAiApi() directly", let's add a public wrapper in AiService.
            // For now, use the same RestTemplate pattern already in AiService.
            return aiService.callAiApiPublic(systemPrompt, userPrompt);
        } catch (Exception e) {
            return "I'm having trouble connecting right now. Keep going — you've got this!";
        }
    }

    private String callAiWithHistory(String systemPrompt, List<Map<String, String>> history) {
        try {
            // Build a single user prompt that includes the full conversation
            StringBuilder conversationBlock = new StringBuilder();
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    conversationBlock.append("User: ").append(content).append("\n");
                } else {
                    conversationBlock.append("Coach: ").append(content).append("\n");
                }
            }
            String userPrompt = "Continue the coaching conversation. Here is the full conversation so far:\n\n"
                    + conversationBlock + "\nRespond as the coach. Maximum 3 sentences.";
            return aiService.callAiApiPublic(systemPrompt, userPrompt);
        } catch (Exception e) {
            return "I'm having trouble connecting right now. Keep going — you've got this!";
        }
    }

    private List<Map<String, String>> parseHistory(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String goalsToJson(List<DailyGoal> goals) {
        try {
            return objectMapper.writeValueAsString(goalsToDTOs(goals));
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<DailyGoalDTO> goalsToDTOs(List<DailyGoal> goals) {
        return goals.stream().map(this::toDTO).toList();
    }

    private DailyGoalDTO toDTO(DailyGoal goal) {
        return new DailyGoalDTO(
                goal.getId(),
                goal.getGoalText(),
                goal.getStatus().name(),
                goal.getGoalDate(),
                goal.getCreatedAt()
        );
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
