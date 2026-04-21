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

        return toDTO(generateAndSaveEntity(user, null));
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

    /**
     * Stores the user's self-reported weakness and IMMEDIATELY regenerates today's challenge
     * so the user sees a personalized result right away.
     */
    public DailyChallengeDTO submitWeaknessHint(String hint) {
        Users user = currentUser();
        LocalDate today = LocalDate.now();

        // Delete today's challenge so we regenerate it fresh with the hint
        dailyChallengeRepo.findByUserIdAndChallengeDate(user.getId(), today)
                .ifPresent(dailyChallengeRepo::delete);

        // Generate a new challenge with the hint forced as the primary signal
        DailyChallenge fresh = generateAndSaveEntity(user, hint);

        activityLogService.log(user.getId(), "DAILY_CHALLENGE_WEAKNESS_HINT", hint);

        return toDTO(fresh);
    }

    // ── Generation logic ──────────────────────────────────────────────────────

    /**
     * @param immediateHint  non-null when the user just submitted a hint → overrides stored hint
     */
    private DailyChallenge generateAndSaveEntity(Users user, String immediateHint) {
        LocalDate today = LocalDate.now();

        // ── 1. Game history analysis ──────────────────────────────────────────
        List<GameResult> results = gameResultRepo
                .findByUserIdOrderByPlayedAtDesc(user.getId())
                .stream().limit(30).collect(Collectors.toList());

        Map<Integer, String> gameTypeById = new HashMap<>();
        for (GameResult r : results) {
            if (!gameTypeById.containsKey(r.getGameId())) {
                gameRepo.findById(r.getGameId())
                        .ifPresent(g -> gameTypeById.put(g.getId(), g.getType()));
            }
        }

        Map<String, DoubleSummaryStatistics> statsByCategory = results.stream()
                .filter(r -> gameTypeById.containsKey(r.getGameId()))
                .filter(r -> GAME_CATEGORY.containsKey(gameTypeById.get(r.getGameId())))
                .collect(Collectors.groupingBy(
                        r -> GAME_CATEGORY.get(gameTypeById.get(r.getGameId())),
                        Collectors.summarizingDouble(GameResult::getFocusScoreGained)
                ));

        // Detected weakness from data (default memory for new users)
        String dataWeakness = "memory";
        if (results.size() >= 5 && !statsByCategory.isEmpty()) {
            dataWeakness = statsByCategory.entrySet().stream()
                    .min(Comparator.comparingDouble(e -> e.getValue().getAverage()))
                    .map(Map.Entry::getKey)
                    .orElse("memory");
        }

        // ── 2. Resolve the hint to use ────────────────────────────────────────
        // immediateHint (from current call) > stored hint from a previous day
        String activeHint = immediateHint;
        if (activeHint == null) {
            activeHint = dailyChallengeRepo
                    .findFirstByUserIdAndUserWeaknessHintIsNotNullOrderByChallengeDateDesc(user.getId())
                    .map(DailyChallenge::getUserWeaknessHint)
                    .orElse(null);
        }

        // ── 3. Activity log context ───────────────────────────────────────────
        List<ActivityLog> recentLogs = activityLogRepo
                .findByUserIdOrderByActivityDateDesc(user.getId())
                .stream().limit(3).collect(Collectors.toList());

        // ── 4. Build prompt context strings ──────────────────────────────────
        StringBuilder perfSummary = new StringBuilder();
        for (Map.Entry<String, DoubleSummaryStatistics> e : statsByCategory.entrySet()) {
            perfSummary.append(String.format("  - %s: avg gain=%.2f over %d sessions%n",
                    e.getKey(), e.getValue().getAverage(), e.getValue().getCount()));
        }
        if (perfSummary.isEmpty()) {
            perfSummary.append("  - No game history yet (new user — default to memory area).\n");
        }

        StringBuilder logContext = new StringBuilder();
        for (ActivityLog log : recentLogs) {
            logContext.append(String.format("  - [%s] %s%n",
                    log.getActivityType(),
                    log.getActivityDescription() != null ? log.getActivityDescription() : ""));
        }

        boolean hasBooks = bookRepo.count() > 0;

        // ── 5. Per-weakness game guidance (prevents the AI from always picking memory_matrix) ──
        String gameGuidance = switch (dataWeakness) {
            case "attention" ->
                "Best games for ATTENTION weakness: color_match (Stroop inhibition) or train_of_thought (divided attention). " +
                "Pick one of these — do NOT suggest memory games.";
            case "speed" ->
                "Best games for SPEED weakness: speed_match (reaction speed) or number_stream (arithmetic speed). " +
                "Pick one of these — do NOT suggest memory or attention games.";
            case "logic" ->
                "Best game for LOGIC weakness: sudoku. Pick sudoku.";
            default ->
                "Best games for MEMORY weakness: memory_matrix (spatial memory) or pattern_trail (sequence memory). " +
                "Alternate between them for variety — avoid always picking the same one.";
        };

        // ── 6. Call the AI ────────────────────────────────────────────────────
        String systemPrompt =
                "You are a cognitive performance coach inside FocusPro. " +
                "Always return valid JSON only — no markdown fences, no explanation, no extra text.";

        String hintBlock = (activeHint != null && !activeHint.isBlank())
                ? "\nUSER SELF-REPORTED WEAKNESS: \"" + activeHint + "\"\n" +
                  ">>> This is the STRONGEST signal. Override the data-driven weakness if they conflict. <<<\n"
                : "";

        String bookBlock = hasBooks
                ? "\nBooks ARE available in the library. You should recommend a BOOK challenge " +
                  "(challengeType=BOOK, targetBookId=null) roughly 30% of the time — especially when " +
                  "the user mentions reading, focus, or concentration issues. A BOOK challenge builds " +
                  "sustained attention and reading focus. When you pick BOOK, set targetGameType=null.\n"
                : "\nNo books are currently available. Do not suggest BOOK challenges.\n";

        String userPrompt = String.format(
                "USER PROFILE\n" +
                "  Name: %s\n" +
                "  Focus Score: %.1f / 100\n" +
                "\n" +
                "GAME PERFORMANCE (last 30 sessions):\n" +
                "%s" +
                "\n" +
                "DATA-DRIVEN WEAKNESS AREA: %s\n" +
                "%s" +
                "\n" +
                "GAME CHOICE GUIDANCE (follow this strictly):\n" +
                "  %s\n" +
                "\n" +
                "%s" +
                "\n" +
                "RECENT ACTIVITY:\n" +
                "%s" +
                "\n" +
                "ALL AVAILABLE GAMES:\n" +
                "  - memory_matrix  → memory     (spatial memory grid)\n" +
                "  - pattern_trail  → memory     (sequence memory)\n" +
                "  - sudoku         → logic      (logical reasoning)\n" +
                "  - speed_match    → speed      (reaction speed)\n" +
                "  - number_stream  → speed      (arithmetic speed)\n" +
                "  - color_match    → attention  (Stroop inhibition)\n" +
                "  - train_of_thought → attention (divided attention)\n" +
                "\n" +
                "TASK: Generate exactly ONE personalized daily challenge for this user.\n" +
                "  - challengeType must be GAME, BOOK, or CUSTOM\n" +
                "  - For GAME: set targetGameType to the exact game id string above; targetBookId = null\n" +
                "  - For BOOK: set targetGameType = null; targetBookId = null (we will assign a book later)\n" +
                "  - For CUSTOM: both targetGameType and targetBookId = null; write a specific cognitive task in challengeDescription\n" +
                "  - challengeTitle: short motivating phrase (max 8 words)\n" +
                "  - challengeDescription: 2-3 sentences explaining WHY this challenge fits this specific user's data\n" +
                "  - weaknessArea: one of: memory, attention, speed, logic, reading\n" +
                "\n" +
                "IMPORTANT: Return ONLY the JSON object below. No markdown. No explanation. No extra keys.\n" +
                "{\n" +
                "  \"challengeType\": \"GAME\",\n" +
                "  \"targetGameType\": \"<exact game id or null>\",\n" +
                "  \"targetBookId\": null,\n" +
                "  \"challengeTitle\": \"<short title>\",\n" +
                "  \"challengeDescription\": \"<why this challenge fits this user>\",\n" +
                "  \"weaknessArea\": \"<memory|attention|speed|logic|reading>\"\n" +
                "}",
                user.getName() != null ? user.getName() : user.getUsername(),
                user.getFocusScore() != null ? user.getFocusScore() : 0.0,
                perfSummary,
                dataWeakness,
                hintBlock,
                gameGuidance,
                bookBlock,
                logContext
        );

        String rawJson = aiService.callAiApiPublic(systemPrompt, userPrompt);

        // ── 7. Parse and persist ──────────────────────────────────────────────
        DailyChallenge challenge = parseAiResponse(rawJson);
        challenge.setUserId(user.getId());
        challenge.setChallengeDate(today);
        challenge.setGeneratedAt(LocalDateTime.now());
        challenge.setExpiresAt(today.atTime(23, 59, 59));

        // Carry the hint forward on the new entity so future queries can find it
        if (activeHint != null) {
            challenge.setUserWeaknessHint(activeHint);
        }

        DailyChallenge saved = dailyChallengeRepo.save(challenge);

        activityLogService.log(user.getId(), "DAILY_CHALLENGE_GENERATED",
                "Generated challenge: type=" + saved.getChallengeType()
                        + ", weakness=" + saved.getWeaknessArea()
                        + (activeHint != null ? ", hint=\"" + activeHint + "\"" : ""));

        return saved;
    }

    private DailyChallenge parseAiResponse(String rawJson) {
        try {
            JsonNode node = objectMapper.readTree(rawJson);

            DailyChallenge c = new DailyChallenge();
            c.setChallengeType(node.path("challengeType").asText("CUSTOM").toUpperCase().trim());
            c.setTargetGameType(nullIfBlank(node.path("targetGameType").asText(null)));
            c.setTargetBookId(node.path("targetBookId").isNull() ? null
                    : (node.path("targetBookId").asInt() == 0 ? null
                       : node.path("targetBookId").asInt()));
            c.setChallengeTitle(node.path("challengeTitle").asText("Today's Challenge"));
            c.setChallengeDescription(node.path("challengeDescription").asText(""));
            c.setWeaknessArea(node.path("weaknessArea").asText("memory").toLowerCase().trim());
            return c;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI challenge response: " + e.getMessage()
                    + " | Raw: " + rawJson, e);
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
        if (s == null) return null;
        String trimmed = s.trim();
        return (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null")
                || trimmed.equals("<exact game id or null>")
                || trimmed.startsWith("<")) ? null : trimmed;
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
