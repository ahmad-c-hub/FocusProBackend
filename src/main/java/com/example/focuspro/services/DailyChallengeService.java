package com.example.focuspro.services;

import com.example.focuspro.dtos.DailyChallengeDTO;
import com.example.focuspro.entities.ActivityLog;
import com.example.focuspro.entities.DailyChallenge;
import com.example.focuspro.entities.GameResult;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.ActivityLogRepo;
import com.example.focuspro.repos.BookRepo;
import com.example.focuspro.repos.DailyChallengeRepo;
import com.example.focuspro.repos.GameRepo;
import com.example.focuspro.repos.GameResultRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DailyChallengeService {

    @Autowired private DailyChallengeRepo dailyChallengeRepo;
    @Autowired private GameResultRepo      gameResultRepo;
    @Autowired private GameRepo            gameRepo;
    @Autowired private BookRepo            bookRepo;
    @Autowired private ActivityLogRepo     activityLogRepo;
    @Autowired private ActivityLogService  activityLogService;
    @Autowired private AiService           aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Maps game type id → cognitive category
    private static final Map<String, String> GAME_CATEGORY = Map.of(
            "memory_matrix",    "memory",
            "pattern_trail",    "memory",
            "sudoku",           "logic",
            "speed_match",      "speed",
            "number_stream",    "speed",
            "color_match",      "attention",
            "train_of_thought", "attention"
    );

    // ── Public API ────────────────────────────────────────────────────────────

    public DailyChallengeDTO getOrGenerateTodayChallenge() {
        Users user = currentUser();
        LocalDate today = LocalDate.now();

        Optional<DailyChallenge> existing =
                dailyChallengeRepo.findByUserIdAndChallengeDate(user.getId(), today);

        if (existing.isPresent()) {
            DailyChallenge c = existing.get();
            if (c.getExpiresAt() != null && c.getExpiresAt().isAfter(LocalDateTime.now())) {
                return toDTO(c);
            }
        }

        return generateAndSave(user);
    }

    public DailyChallengeDTO completeChallenge(Long challengeId) {
        Users user = currentUser();

        DailyChallenge challenge = dailyChallengeRepo.findById(challengeId)
                .orElseThrow(() -> new RuntimeException("Challenge not found: " + challengeId));

        if (challenge.getUserId() != user.getId()) {
            throw new RuntimeException("Challenge does not belong to this user");
        }

        challenge.setCompletedAt(LocalDateTime.now());
        dailyChallengeRepo.save(challenge);

        activityLogService.log(user.getId(), "DAILY_CHALLENGE_COMPLETED",
                "Completed daily challenge: " + challenge.getChallengeTitle());

        return toDTO(challenge);
    }

    public DailyChallengeDTO submitWeaknessHint(String hint) {
        Users user = currentUser();
        LocalDate today = LocalDate.now();

        DailyChallenge challenge;
        Optional<DailyChallenge> existing =
                dailyChallengeRepo.findByUserIdAndChallengeDate(user.getId(), today);

        if (existing.isPresent()) {
            challenge = existing.get();
        } else {
            challenge = generateAndSaveEntity(user);
        }

        challenge.setUserWeaknessHint(hint);
        dailyChallengeRepo.save(challenge);

        activityLogService.log(user.getId(), "DAILY_CHALLENGE_WEAKNESS_HINT", hint);

        return toDTO(challenge);
    }

    // ── Generation logic ──────────────────────────────────────────────────────

    private DailyChallengeDTO generateAndSave(Users user) {
        return toDTO(generateAndSaveEntity(user));
    }

    private DailyChallenge generateAndSaveEntity(Users user) {
        LocalDate today = LocalDate.now();

        // Load last 30 game results for this user
        List<GameResult> results = gameResultRepo
                .findByUserIdOrderByPlayedAtDesc(user.getId())
                .stream().limit(30).collect(Collectors.toList());

        // Build gameId → game type map (one DB hit per distinct gameId)
        Map<Integer, String> gameTypeById = new HashMap<>();
        for (GameResult r : results) {
            if (!gameTypeById.containsKey(r.getGameId())) {
                gameRepo.findById(r.getGameId())
                        .ifPresent(g -> gameTypeById.put(g.getId(), g.getType()));
            }
        }

        // Aggregate focusScoreGained by cognitive category
        Map<String, DoubleSummaryStatistics> statsByCategory = results.stream()
                .filter(r -> gameTypeById.containsKey(r.getGameId()))
                .filter(r -> GAME_CATEGORY.containsKey(gameTypeById.get(r.getGameId())))
                .collect(Collectors.groupingBy(
                        r -> GAME_CATEGORY.get(gameTypeById.get(r.getGameId())),
                        Collectors.summarizingDouble(GameResult::getFocusScoreGained)
                ));

        // Find weakest category (lowest average focusScoreGained)
        String weaknessArea = "memory"; // default for new users with < 5 results
        if (results.size() >= 5 && !statsByCategory.isEmpty()) {
            weaknessArea = statsByCategory.entrySet().stream()
                    .min(Comparator.comparingDouble(e -> e.getValue().getAverage()))
                    .map(Map.Entry::getKey)
                    .orElse("memory");
        }

        // Load previous userWeaknessHint if any
        Optional<DailyChallenge> hintSource =
                dailyChallengeRepo.findFirstByUserIdAndUserWeaknessHintIsNotNullOrderByChallengeDateDesc(user.getId());
        String previousHint = hintSource.map(DailyChallenge::getUserWeaknessHint).orElse(null);

        // Load 3 most recent activity logs for context
        List<ActivityLog> recentLogs = activityLogRepo
                .findByUserIdOrderByActivityDateDesc(user.getId())
                .stream().limit(3).collect(Collectors.toList());

        // Build game performance summary for the prompt
        StringBuilder perfSummary = new StringBuilder();
        for (Map.Entry<String, DoubleSummaryStatistics> entry : statsByCategory.entrySet()) {
            DoubleSummaryStatistics stats = entry.getValue();
            perfSummary.append(String.format("  - %s: avg focusScore gain=%.2f over %d sessions%n",
                    entry.getKey(), stats.getAverage(), stats.getCount()));
        }
        if (perfSummary.isEmpty()) {
            perfSummary.append("  - No game history yet (new user).\n");
        }

        // Build activity log context
        StringBuilder logContext = new StringBuilder();
        for (ActivityLog log : recentLogs) {
            logContext.append(String.format("  - %s: %s%n",
                    log.getActivityType(),
                    log.getActivityDescription() != null ? log.getActivityDescription() : ""));
        }

        boolean hasBooks = bookRepo.count() > 0;

        String systemPrompt = """
                You are a cognitive performance coach inside FocusPro. \
                Always return valid JSON only — no markdown, no explanation, no extra text.""";

        String userPrompt = String.format("""
                User: %s
                Focus Score: %.1f / 100

                Game performance summary (last 30 sessions):
                %s
                Detected weakest cognitive area: %s
                %s
                Recent activity (last 3 logs):
                %s
                Books available in the system: %s

                Available games and what they train:
                  - memory_matrix → category: memory. Trains short-term spatial memory.
                  - pattern_trail → category: memory. Trains sequence memory.
                  - sudoku → category: logic. Trains logical reasoning and planning.
                  - speed_match → category: speed. Trains processing speed.
                  - number_stream → category: speed. Trains arithmetic speed.
                  - color_match → category: attention. Trains cognitive control and Stroop inhibition.
                  - train_of_thought → category: attention. Trains divided attention and multitasking.

                Generate one personalized daily challenge. If the weakness is memory or attention, \
                recommend a specific game. If the user has low reading activity and books are available, \
                recommend a book session. Otherwise generate a custom cognitive task. \
                Return exactly this JSON shape with no markdown fences and no extra text:
                {
                  "challengeType": "GAME",
                  "targetGameType": "memory_matrix",
                  "targetBookId": null,
                  "challengeTitle": "Train your memory today",
                  "challengeDescription": "Play Memory Matrix and aim for level 5 or higher. Your spatial memory scores are the lowest area in your recent sessions — this game targets it directly.",
                  "weaknessArea": "memory"
                }
                challengeType must be GAME, BOOK, or CUSTOM. \
                targetGameType is only set when challengeType=GAME. \
                targetBookId is only set when challengeType=BOOK. \
                For CUSTOM, both are null and challengeDescription is the full task. \
                Do not include markdown code fences. Return only the JSON object.
                """,
                user.getName() != null ? user.getName() : user.getUsername(),
                user.getFocusScore() != null ? user.getFocusScore() : 0.0,
                perfSummary,
                weaknessArea,
                previousHint != null
                        ? "The user has told us they personally feel weak at: \"" + previousHint + "\". Treat this as a strong signal.\n"
                        : "",
                logContext,
                hasBooks ? "yes" : "no"
        );

        String rawJson = aiService.callAiApiPublic(systemPrompt, userPrompt);

        DailyChallenge challenge = parseAiResponse(rawJson);
        challenge.setUserId(user.getId());
        challenge.setChallengeDate(today);
        challenge.setGeneratedAt(LocalDateTime.now());
        challenge.setExpiresAt(today.atTime(23, 59, 59));

        DailyChallenge saved = dailyChallengeRepo.save(challenge);

        activityLogService.log(user.getId(), "DAILY_CHALLENGE_GENERATED",
                "Generated daily challenge: type=" + saved.getChallengeType()
                        + ", weakness=" + saved.getWeaknessArea());

        return saved;
    }

    private DailyChallenge parseAiResponse(String rawJson) {
        try {
            JsonNode node = objectMapper.readTree(rawJson);

            DailyChallenge c = new DailyChallenge();
            c.setChallengeType(node.path("challengeType").asText("CUSTOM"));
            c.setTargetGameType(nullIfBlank(node.path("targetGameType").asText(null)));
            c.setTargetBookId(node.path("targetBookId").isNull() ? null
                    : node.path("targetBookId").asInt());
            c.setChallengeTitle(node.path("challengeTitle").asText("Today's Challenge"));
            c.setChallengeDescription(node.path("challengeDescription").asText(""));
            c.setWeaknessArea(node.path("weaknessArea").asText("memory"));
            return c;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI challenge response: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DailyChallengeDTO toDTO(DailyChallenge c) {
        boolean expired = c.getExpiresAt() != null
                && c.getExpiresAt().isBefore(LocalDateTime.now());
        boolean completed = c.getCompletedAt() != null;
        return new DailyChallengeDTO(
                c.getId(),
                c.getChallengeType(),
                c.getTargetGameType(),
                c.getTargetBookId(),
                c.getChallengeTitle(),
                c.getChallengeDescription(),
                c.getWeaknessArea(),
                c.getChallengeDate(),
                c.getCompletedAt(),
                c.getExpiresAt(),
                expired,
                completed
        );
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank() || s.equals("null")) ? null : s;
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
