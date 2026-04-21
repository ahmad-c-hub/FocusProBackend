package com.example.focuspro.services;

import com.example.focuspro.dtos.DailyChallengeDTO;
import com.example.focuspro.entities.ActivityLog;
import com.example.focuspro.entities.Book;
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

    // game type id → cognitive category
    private static final Map<String, String> GAME_CATEGORY = Map.of(
            "memory_matrix",    "memory",
            "pattern_trail",    "memory",
            "sudoku",           "logic",
            "speed_match",      "speed",
            "number_stream",    "speed",
            "color_match",      "attention",
            "train_of_thought", "attention"
    );

    // Rotation pool — used for new users with < 5 game results
    // Different users + different days → different starting areas
    private static final String[] ROTATION_AREAS =
            {"memory", "attention", "speed", "logic", "reading"};

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
                "Completed: " + challenge.getChallengeTitle());

        return toDTO(challenge);
    }

    /**
     * Immediately deletes today's cached challenge and regenerates a fresh one
     * with the user's hint as the strongest signal. The user sees the new challenge
     * right away — no need to wait until tomorrow.
     */
    public DailyChallengeDTO submitWeaknessHint(String hint) {
        Users user = currentUser();
        LocalDate today = LocalDate.now();

        // Delete today's challenge so regeneration is forced
        dailyChallengeRepo.findByUserIdAndChallengeDate(user.getId(), today)
                .ifPresent(dailyChallengeRepo::delete);

        DailyChallenge fresh = generateAndSaveEntity(user, hint);

        activityLogService.log(user.getId(), "DAILY_CHALLENGE_WEAKNESS_HINT", hint);

        return toDTO(fresh);
    }

    // ── Core generation ───────────────────────────────────────────────────────

    private DailyChallenge generateAndSaveEntity(Users user, String immediateHint) {
        LocalDate today = LocalDate.now();

        // ── Step 1: Analyse game history ──────────────────────────────────────
        List<GameResult> results = gameResultRepo
                .findByUserIdOrderByPlayedAtDesc(user.getId())
                .stream().limit(30).collect(Collectors.toList());

        // gameId → game type string
        Map<Integer, String> gameTypeById = new HashMap<>();
        for (GameResult r : results) {
            gameTypeById.computeIfAbsent(r.getGameId(),
                    id -> gameRepo.findById(id).map(g -> g.getType()).orElse(null));
        }
        gameTypeById.values().removeIf(v -> v == null);

        // Aggregate avg focusScoreGained per cognitive category
        Map<String, DoubleSummaryStatistics> statsByCategory = results.stream()
                .filter(r -> gameTypeById.containsKey(r.getGameId()))
                .filter(r -> GAME_CATEGORY.containsKey(gameTypeById.get(r.getGameId())))
                .collect(Collectors.groupingBy(
                        r -> GAME_CATEGORY.get(gameTypeById.get(r.getGameId())),
                        Collectors.summarizingDouble(GameResult::getFocusScoreGained)
                ));

        // Per-game type breakdown (for the prompt, so the AI sees details not just categories)
        Map<String, DoubleSummaryStatistics> statsByGameType = results.stream()
                .filter(r -> gameTypeById.containsKey(r.getGameId()))
                .collect(Collectors.groupingBy(
                        r -> gameTypeById.get(r.getGameId()),
                        Collectors.summarizingDouble(GameResult::getFocusScoreGained)
                ));

        // ── Step 2: Determine primary weakness area ───────────────────────────
        String dataWeakness;
        boolean hasEnoughHistory = results.size() >= 5 && !statsByCategory.isEmpty();

        if (hasEnoughHistory) {
            dataWeakness = statsByCategory.entrySet().stream()
                    .min(Comparator.comparingDouble(e -> e.getValue().getAverage()))
                    .map(Map.Entry::getKey)
                    .orElse(rotatingDefault(user.getId(), today));
        } else {
            // No history — rotate so every account gets variety, not always "memory"
            dataWeakness = rotatingDefault(user.getId(), today);
        }

        // ── Step 3: Resolve hint ──────────────────────────────────────────────
        String activeHint = immediateHint;
        if (activeHint == null) {
            activeHint = dailyChallengeRepo
                    .findFirstByUserIdAndUserWeaknessHintIsNotNullOrderByChallengeDateDesc(user.getId())
                    .map(DailyChallenge::getUserWeaknessHint)
                    .orElse(null);
        }

        // ── Step 4: Load books ────────────────────────────────────────────────
        // Pass real books (id + title + category + description snippet) so the AI
        // can recommend a SPECIFIC book and return its real ID.
        List<Book> books = bookRepo.findAll().stream()
                .limit(20)
                .collect(Collectors.toList());

        // ── Step 5: Recent activity context ──────────────────────────────────
        List<ActivityLog> recentLogs = activityLogRepo
                .findByUserIdOrderByActivityDateDesc(user.getId())
                .stream().limit(5).collect(Collectors.toList());

        // ── Step 6: Build prompt strings ──────────────────────────────────────
        // -- game performance per type (not just category, to show variety in data)
        StringBuilder gameStats = new StringBuilder();
        if (statsByGameType.isEmpty()) {
            gameStats.append("  (no game sessions yet)\n");
        } else {
            statsByGameType.entrySet().stream()
                    .sorted(Comparator.comparingDouble(e -> e.getValue().getAverage()))
                    .forEach(e -> gameStats.append(String.format(
                            "  - %-20s  category=%-10s  avg_score_gain=%.2f  sessions=%d%n",
                            e.getKey(),
                            GAME_CATEGORY.getOrDefault(e.getKey(), "?"),
                            e.getValue().getAverage(),
                            e.getValue().getCount())));
        }

        // -- category summary
        StringBuilder catStats = new StringBuilder();
        if (statsByCategory.isEmpty()) {
            catStats.append("  (no category data yet)\n");
        } else {
            statsByCategory.entrySet().stream()
                    .sorted(Comparator.comparingDouble(e -> e.getValue().getAverage()))
                    .forEach(e -> catStats.append(String.format(
                            "  - %-12s  avg_gain=%.2f  sessions=%d%n",
                            e.getKey(), e.getValue().getAverage(), e.getValue().getCount())));
        }

        // -- books for the AI to choose from
        StringBuilder booksBlock = new StringBuilder();
        if (books.isEmpty()) {
            booksBlock.append("  (no books in library)\n");
        } else {
            for (Book b : books) {
                String desc = b.getDescription() != null
                        ? b.getDescription().substring(0, Math.min(80, b.getDescription().length())) + "..."
                        : "";
                booksBlock.append(String.format(
                        "  - id=%d  \"%s\" by %s  [category: %s, level: %d]  — %s%n",
                        b.getId(),
                        b.getTitle(),
                        b.getAuthor() != null ? b.getAuthor() : "Unknown",
                        b.getCategory(),
                        b.getLevel() != null ? b.getLevel() : 0,
                        desc));
            }
        }

        // -- recent activity
        StringBuilder activityBlock = new StringBuilder();
        for (ActivityLog log : recentLogs) {
            activityBlock.append(String.format("  [%s] %s%n",
                    log.getActivityType(),
                    log.getActivityDescription() != null ? log.getActivityDescription() : ""));
        }

        // ── Step 7: Build the prompt ──────────────────────────────────────────
        String systemPrompt =
                "You are a world-class cognitive performance coach inside FocusPro. " +
                "You make highly personalised, varied recommendations that genuinely help users improve. " +
                "Always return valid JSON only — no markdown, no explanation, no extra text.";

        String hintSection = (activeHint != null && !activeHint.isBlank())
                ? "\n=== USER'S OWN WORDS (HIGHEST PRIORITY SIGNAL — override everything else) ===\n" +
                  "\"" + activeHint + "\"\n" +
                  "You MUST build the challenge around what the user just told you. " +
                  "Ignore the data-driven weakness if it conflicts with this.\n" +
                  "==========================================================================\n"
                : "";

        String challengeTypeInstruction = books.isEmpty()
                ? "You must pick challengeType = GAME or CUSTOM only (no books in library)."
                : "Pick challengeType as follows:\n" +
                  "  • GAME — to target a specific cognitive skill through play (pick a game matching the weakness below)\n" +
                  "  • BOOK — to build reading focus or learn about a relevant topic (pick a SPECIFIC book from the list below by its id)\n" +
                  "  • CUSTOM — for a non-game cognitive task (mindfulness, journaling, timed writing, etc.)\n" +
                  "  IMPORTANT: You MUST recommend a BOOK challenge at least 35% of the time. Do not default to games every time.";

        String userPrompt =
                "=== USER PROFILE ===\n" +
                "Name: " + (user.getName() != null ? user.getName() : user.getUsername()) + "\n" +
                "Focus Score: " + String.format("%.1f", user.getFocusScore() != null ? user.getFocusScore() : 0.0) + " / 100\n" +
                "Total game sessions in history: " + results.size() + "\n" +
                hintSection +
                "\n=== GAME PERFORMANCE (sorted worst → best) ===\n" +
                "Per game type:\n" + gameStats +
                "\nPer cognitive category:\n" + catStats +
                "\nData-driven weakness (lowest average score gain): " + dataWeakness + "\n" +
                (hasEnoughHistory ? "" : "(Note: fewer than 5 sessions — this is an estimated starting area, not confirmed weakness)\n") +
                "\n=== AVAILABLE GAMES ===\n" +
                "  Game ID            | Category  | What it trains\n" +
                "  -------------------|-----------|------------------------------------------------\n" +
                "  memory_matrix      | memory    | Short-term spatial memory — remembering grid patterns\n" +
                "  pattern_trail      | memory    | Sequence memory — remembering dot order\n" +
                "  sudoku             | logic     | Logical deduction and planning\n" +
                "  speed_match        | speed     | Reaction speed and quick decisions\n" +
                "  number_stream      | speed     | Arithmetic speed under pressure\n" +
                "  color_match        | attention | Cognitive control and Stroop inhibition\n" +
                "  train_of_thought   | attention | Divided attention and multitasking\n" +
                "\n=== AVAILABLE BOOKS (pick one by its id if you choose BOOK) ===\n" +
                booksBlock +
                "\n=== RECENT ACTIVITY (last 5 events) ===\n" +
                activityBlock +
                "\n=== YOUR TASK ===\n" +
                challengeTypeInstruction + "\n" +
                "\n" +
                "Rules for GAME choice:\n" +
                "  • weakness=memory    → pick memory_matrix OR pattern_trail (not always the same one)\n" +
                "  • weakness=attention → pick color_match OR train_of_thought\n" +
                "  • weakness=speed     → pick speed_match OR number_stream\n" +
                "  • weakness=logic     → pick sudoku\n" +
                "  • If the user's hint mentions a specific game area, map it and follow the rule above\n" +
                "\n" +
                "Rules for BOOK choice:\n" +
                "  • Look at the book list above. Find the book whose category and description BEST matches\n" +
                "    the user's weakness or personal hint.\n" +
                "  • Set targetBookId to that book's numeric id.\n" +
                "  • In challengeDescription, explain WHY this specific book will help this specific user.\n" +
                "\n" +
                "Rules for CUSTOM choice:\n" +
                "  • Describe a specific, actionable cognitive task in challengeDescription.\n" +
                "    Example: 'Set a 25-minute focus timer, turn off all notifications, and write a 200-word\n" +
                "    summary of one thing you want to improve this week. This trains sustained focus.'\n" +
                "\n" +
                "OUTPUT FORMAT — return ONLY this JSON, no markdown, no extra text:\n" +
                "{\n" +
                "  \"challengeType\": \"GAME or BOOK or CUSTOM\",\n" +
                "  \"targetGameType\": \"exact_game_id or null\",\n" +
                "  \"targetBookId\": null_or_integer,\n" +
                "  \"challengeTitle\": \"short motivating title (max 7 words)\",\n" +
                "  \"challengeDescription\": \"2-3 sentences explaining why this specific challenge fits this user's data and situation\",\n" +
                "  \"weaknessArea\": \"memory or attention or speed or logic or reading\"\n" +
                "}";

        String rawJson = aiService.callAiApiPublic(systemPrompt, userPrompt);

        // ── Step 8: Parse + persist ───────────────────────────────────────────
        DailyChallenge challenge = parseAiResponse(rawJson);
        challenge.setUserId(user.getId());
        challenge.setChallengeDate(today);
        challenge.setGeneratedAt(LocalDateTime.now());
        challenge.setExpiresAt(today.atTime(23, 59, 59));
        if (activeHint != null) challenge.setUserWeaknessHint(activeHint);

        DailyChallenge saved = dailyChallengeRepo.save(challenge);

        activityLogService.log(user.getId(), "DAILY_CHALLENGE_GENERATED",
                String.format("type=%s, game=%s, bookId=%s, weakness=%s, hint=%s",
                        saved.getChallengeType(),
                        saved.getTargetGameType(),
                        saved.getTargetBookId(),
                        saved.getWeaknessArea(),
                        activeHint != null ? "\"" + activeHint + "\"" : "none"));

        return saved;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Produces a different starting area for each user on each day of the year.
     * userId=1 on dayOfYear=1 → index (1*7 + 1) % 5 = 0 → "memory"
     * userId=2 on dayOfYear=1 → index (2*7 + 1) % 5 = 1 → "attention"
     * userId=1 on dayOfYear=2 → index (1*7 + 2) % 5 = 4 → "reading"
     * This guarantees different accounts on the same day never all get the same area.
     */
    private String rotatingDefault(int userId, LocalDate date) {
        int index = Math.abs((userId * 7 + date.getDayOfYear()) % ROTATION_AREAS.length);
        return ROTATION_AREAS[index];
    }

    private DailyChallenge parseAiResponse(String rawJson) {
        try {
            JsonNode node = objectMapper.readTree(rawJson);

            DailyChallenge c = new DailyChallenge();
            String type = node.path("challengeType").asText("CUSTOM")
                    .toUpperCase().trim();
            c.setChallengeType(type);
            c.setTargetGameType(nullIfBlank(node.path("targetGameType").asText(null)));
            c.setWeaknessArea(node.path("weaknessArea").asText("memory")
                    .toLowerCase().trim());
            c.setChallengeTitle(node.path("challengeTitle").asText("Today's Challenge"));
            c.setChallengeDescription(node.path("challengeDescription").asText(""));

            // targetBookId — handle both number and null
            JsonNode bookIdNode = node.path("targetBookId");
            if (!bookIdNode.isMissingNode() && !bookIdNode.isNull()
                    && bookIdNode.isNumber() && bookIdNode.asInt() > 0) {
                c.setTargetBookId(bookIdNode.asInt());
            } else {
                c.setTargetBookId(null);
            }

            // If type is GAME but no valid game type, fall back to CUSTOM
            if ("GAME".equals(type) && c.getTargetGameType() == null) {
                c.setChallengeType("CUSTOM");
            }

            return c;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse AI challenge response: " + e.getMessage() + " | raw: " + rawJson, e);
        }
    }

    private DailyChallengeDTO toDTO(DailyChallenge c) {
        boolean expired  = c.getExpiresAt() != null
                && c.getExpiresAt().isBefore(LocalDateTime.now());
        boolean completed = c.getCompletedAt() != null;
        return new DailyChallengeDTO(
                c.getId(), c.getChallengeType(), c.getTargetGameType(), c.getTargetBookId(),
                c.getChallengeTitle(), c.getChallengeDescription(), c.getWeaknessArea(),
                c.getChallengeDate(), c.getCompletedAt(), c.getExpiresAt(),
                expired, completed);
    }

    private String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        // Reject template placeholders and the literal word "null"
        if (t.isEmpty() || t.equalsIgnoreCase("null") || t.startsWith("<") || t.startsWith("exact")) return null;
        return t;
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
