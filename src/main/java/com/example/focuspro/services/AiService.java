package com.example.focuspro.services;

import com.example.focuspro.dtos.*;
import com.example.focuspro.entities.AiGeneratedQuestion;
import com.example.focuspro.entities.BookSnippet;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.AiGeneratedQuestionRepo;
import com.example.focuspro.repos.BookSnippetRepo;
import com.example.focuspro.repos.UserRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AiService {

    // ── Dependencies ─────────────────────────────────────────────────────────

    @Autowired private AiGeneratedQuestionRepo aiQuestionRepo;
    @Autowired private BookSnippetRepo          bookSnippetRepo;
    @Autowired private UserRepo                 userRepo;
    @Autowired private JdbcTemplate             jdbcTemplate;
    @Autowired private ActivityLogService       activityLogService;

    // ── Config — set these three lines in application.properties ─────────────
    //
    // FOR GEMINI (demo — free, no card):
    //   ai.api.url = https://generativelanguage.googleapis.com/v1beta/openai/chat/completions
    //   ai.api.key = YOUR_KEY_FROM_aistudio.google.com
    //   ai.model   = gemini-2.5-flash
    //
    // FOR GROQ (free, fastest):
    //   ai.api.url = https://api.groq.com/openai/v1/chat/completions
    //   ai.api.key = YOUR_KEY_FROM_console.groq.com
    //   ai.model   = llama-3.3-70b-versatile
    //
    // FOR CLAUDE (when you get the key):
    //   ai.api.url = https://api.anthropic.com/v1/messages
    //   ai.api.key = sk-ant-YOUR_KEY
    //   ai.model   = claude-sonnet-4-20250514
    //   NOTE: also flip useAnthropicFormat=true below — Claude has a different request shape
    //
    // FOR OPENAI:
    //   ai.api.url = https://api.openai.com/v1/chat/completions
    //   ai.api.key = sk-YOUR_KEY
    //   ai.model   = gpt-4o-mini

    @Value("${ai.api.url}")
    private String aiApiUrl;

    @Value("${ai.api.key}")
    private String aiApiKey;

    @Value("${ai.model}")
    private String aiModel;

    // Set to true ONLY when using Claude (Anthropic) directly.
    // Gemini, Groq, OpenAI are all OpenAI-compatible — leave this false.
    @Value("${ai.use.anthropic.format:false}")
    private boolean useAnthropicFormat;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 1. Generate snippet comprehension questions ──────────────────────────

    /**
     * Called right after the user finishes a snippet.
     * Returns 3 MCQ questions specific to that snippet + personalised to the user's level.
     * Cached per user+snippet — safe to call multiple times without re-hitting the AI.
     */
    public List<AiQuestionDTO> generateSnippetQuestions(Integer snippetId) {
        Users user = currentUser();

        // Delete any previously generated questions for this user+snippet
        // so the AI always generates a fresh set on every attempt
        aiQuestionRepo.deleteByUserIdAndSnippetIdAndQuestionType(user.getId(), snippetId, "SNIPPET");

        BookSnippet snippet = bookSnippetRepo.findById(snippetId)
                .orElseThrow(() -> new IllegalArgumentException("Snippet not found: " + snippetId));

        String difficulty = focusScoreToDifficultyLabel(user.getFocusScore());

        String systemPrompt = """
                You are an educational AI inside FocusPro, a focus and productivity app.
                Generate multiple-choice comprehension questions that test genuine understanding,
                not just surface memorisation.
                Always return valid JSON only — no markdown, no explanation, no extra text.
                """;

        String userPrompt = String.format("""
                The user just finished reading this snippet:

                TITLE: %s
                TEXT:
                %s

                User focus score: %.1f / 100 → difficulty: %s
                - LOW:    test basic recall and main idea
                - MEDIUM: test inference and key-point understanding
                - HIGH:   test critical thinking and connections between ideas

                Generate exactly 3 multiple-choice questions grounded in this snippet.

                Return a JSON array with exactly 3 objects, each shaped exactly like this:
                {
                  "questionText": "...",
                  "optionA": "...",
                  "optionB": "...",
                  "optionC": "...",
                  "optionD": "...",
                  "correctAnswer": "A"
                }

                Rules:
                - Every question must be answerable from the snippet text alone
                - All 4 options must be plausible — no obviously wrong answers
                - Exactly one correct answer per question
                - Do not include the letter inside the option text itself
                """,
                snippet.getSnippetTitle(),
                snippet.getSnippetText(),
                user.getFocusScore() != null ? user.getFocusScore() : 0.0,
                difficulty
        );

        String rawJson = callAiApi(systemPrompt, userPrompt);

        List<AiGeneratedQuestion> questions = parseAndSave(
                rawJson, user.getId(), snippetId, "SNIPPET",
                focusScoreToLevel(user.getFocusScore())
        );

        activityLogService.log(user.getId(), "AI_SNIPPET_QUESTIONS_GENERATED",
                "AI generated comprehension questions for: " + snippet.getSnippetTitle());

        return questions.stream().map(this::toQuestionDTO).toList();
    }

    // ── 2. Submit snippet comprehension answers ──────────────────────────────

    /**
     * Scores the user's answers. Pass threshold = 2/3 correct.
     * On pass  → marks snippet complete + awards focus score.
     * On fail  → snippet stays incomplete, no score awarded.
     */
    public SnippetCheckResponse submitSnippetAnswers(Integer snippetId,
                                                     Map<Long, String> answers) {
        Users user = currentUser();

        List<AiGeneratedQuestion> questions = aiQuestionRepo
                .findByUserIdAndSnippetIdAndQuestionType(user.getId(), snippetId, "SNIPPET");

        if (questions.isEmpty()) {
            throw new IllegalStateException(
                    "No questions found for this snippet — call GET /ai/snippet/{id}/questions first.");
        }

        List<AiAnswerResultDTO> results = scoreAnswers(questions, answers);
        int correct      = (int) results.stream().filter(AiAnswerResultDTO::isCorrect).count();
        int total        = questions.size();
        boolean passed   = correct >= 2;

        double focusGained   = 0.0;
        double currentScore  = user.getFocusScore() != null ? user.getFocusScore() : 0.0;
        double newFocusScore = currentScore;

        if (passed) {
            jdbcTemplate.update("""
                    INSERT INTO user_snippet_progress (user_id, snippet_id, consumed_via, completed)
                    VALUES (?, ?, 'text', true)
                    ON CONFLICT (user_id, snippet_id) DO UPDATE SET completed = true
                    """, user.getId(), snippetId);

            BookSnippet snippet = bookSnippetRepo.findById(snippetId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Snippet not found: " + snippetId));

            java.math.BigDecimal base = snippet.getFocusPoints() != null
                    ? snippet.getFocusPoints()
                    : java.math.BigDecimal.valueOf(1.5);

            focusGained = correct == 3
                    ? base.doubleValue()
                    : base.multiply(java.math.BigDecimal.valueOf(0.6)).doubleValue();

            focusGained = Math.round(focusGained * 10.0) / 10.0;
            newFocusScore = Math.min(100.0, currentScore + focusGained);
            user.setFocusScore(newFocusScore);
            userRepo.save(user);

            activityLogService.log(user.getId(), "AI_COMPREHENSION_PASSED",
                    String.format("Passed snippet %d — %d/%d correct, +%.1f pts",
                            snippetId, correct, total, focusGained));
        } else {
            activityLogService.log(user.getId(), "AI_COMPREHENSION_FAILED",
                    String.format("Failed snippet %d — only %d/%d correct", snippetId, correct, total));
        }

        String message = passed
                ? String.format("Great job! %d/%d correct. +%.1f focus points earned.", correct, total, focusGained)
                : String.format("%d/%d correct — you need at least 2 to pass. Review the snippet and try again.", correct, total);

        return new SnippetCheckResponse(correct, total, passed, focusGained, newFocusScore, results, message);
    }

    // ── 3. Generate retention test ───────────────────────────────────────────

    /**
     * Generates a retention audit from 3 random snippets the user completed in the past.
     * Always freshly generated — no caching — so the user cannot memorise answers.
     */
    public List<AiQuestionDTO> generateRetentionTest() {
        Users user = currentUser();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT usp.snippet_id, bs.snippet_title, bs.snippet_text
                FROM user_snippet_progress usp
                JOIN book_snippets bs ON bs.id = usp.snippet_id
                WHERE usp.user_id = ? AND usp.completed = true
                ORDER BY RANDOM()
                LIMIT 3
                """,
                user.getId()
        );

        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "Not enough completed snippets for a retention test yet.");
        }

        StringBuilder snippetsBlock = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            snippetsBlock.append(String.format(
                    "SNIPPET %d — %s:\n%s\n\n",
                    i + 1, row.get("snippet_title"), row.get("snippet_text")));
        }

        String difficulty = focusScoreToDifficultyLabel(user.getFocusScore());

        String systemPrompt = """
                You are an educational AI inside FocusPro.
                Test whether users have retained knowledge from reading done days or weeks ago.
                Always return valid JSON only — no markdown, no explanation, no extra text.
                """;

        String userPrompt = String.format("""
                The user previously read these snippets and is now being tested on retention:

                %s

                User focus score: %.1f / 100 → difficulty: %s

                Generate exactly %d questions — one per snippet above.
                Each question must be clearly answerable from its own snippet only.

                Return a JSON array with exactly %d objects, each shaped exactly like this:
                {
                  "questionText": "...",
                  "optionA": "...",
                  "optionB": "...",
                  "optionC": "...",
                  "optionD": "...",
                  "correctAnswer": "A"
                }

                Rules:
                - One question per snippet — do not mix content across snippets
                - All 4 options must be plausible
                - Exactly one correct answer per question
                """,
                snippetsBlock,
                user.getFocusScore() != null ? user.getFocusScore() : 0.0,
                difficulty,
                rows.size(),
                rows.size()
        );

        String rawJson = callAiApi(systemPrompt, userPrompt);

        List<AiGeneratedQuestion> questions = parseAndSave(
                rawJson, user.getId(), null, "RETENTION",
                focusScoreToLevel(user.getFocusScore())
        );

        activityLogService.log(user.getId(), "AI_RETENTION_TEST_GENERATED",
                "Retention test generated from " + rows.size() + " past snippets");

        return questions.stream().map(this::toQuestionDTO).toList();
    }

    // ── 4. Submit retention test answers ─────────────────────────────────────

    /**
     * Score >= 66%  → +1.0 focus pts
     * Score 33-66%  → no change
     * Score <  33%  → -1.5 focus pts (score can drop)
     */
    public RetentionTestResponse submitRetentionTest(Map<Long, String> answers) {
        Users user = currentUser();

        List<AiGeneratedQuestion> questions = aiQuestionRepo
                .findByUserIdAndQuestionTypeOrderByCreatedAtDesc(user.getId(), "RETENTION")
                .stream()
                .filter(q -> answers.containsKey(q.getId()))
                .toList();

        if (questions.isEmpty()) {
            throw new IllegalStateException(
                    "No retention test found — generate one first via GET /ai/retention/generate.");
        }

        List<AiAnswerResultDTO> results = scoreAnswers(questions, answers);
        int correct    = (int) results.stream().filter(AiAnswerResultDTO::isCorrect).count();
        int total      = questions.size();
        double pct     = (double) correct / total;

        double current = user.getFocusScore() != null ? user.getFocusScore() : 0.0;
        double delta;
        String message;

        if (pct >= 0.66) {
            delta   = 1.0;
            message = String.format("Strong retention! %d/%d correct. +%.1f focus points.", correct, total, delta);
        } else if (pct >= 0.33) {
            delta   = 0.0;
            message = String.format("Decent retention. %d/%d correct. Score unchanged — keep reading.", correct, total);
        } else {
            delta   = -1.5;
            message = String.format("Weak retention. %d/%d correct. %.1f focus points lost.", correct, total, Math.abs(delta));
        }

        double newScore = Math.max(0.0, Math.min(100.0, current + delta));
        user.setFocusScore(newScore);
        userRepo.save(user);

        activityLogService.log(user.getId(), "AI_RETENTION_TEST_SUBMITTED",
                String.format("Retention result: %d/%d, delta=%.1f, new score=%.1f",
                        correct, total, delta, newScore));

        return new RetentionTestResponse(correct, total, delta, newScore, results, message);
    }

    // ── Public wrapper for other services to call the AI ─────────────────────

    /** Public entry point for any service that needs to call the AI without MCQ logic. */
    public String callAiApiPublic(String systemPrompt, String userPrompt) {
        return callAiApi(systemPrompt, userPrompt);
    }

    // ── AI API call (OpenAI-compatible format — works with Gemini, Groq, OpenAI) ──

    private String callAiApi(String systemPrompt, String userPrompt) {
        if (useAnthropicFormat) {
            return callAnthropicApi(systemPrompt, userPrompt);
        }
        return callOpenAiCompatibleApi(systemPrompt, userPrompt);
    }

    /** Used for Gemini, Groq, OpenAI, OpenRouter — all speak the same format. */
    private String callOpenAiCompatibleApi(String systemPrompt, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiApiKey); // Authorization: Bearer YOUR_KEY

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiModel);
        body.put("max_tokens", 4096);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userPrompt)
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        int maxAttempts = 3;
        int delayMs     = 2000; // 2 s → 4 s → give up
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<String> response =
                        restTemplate.postForEntity(aiApiUrl, entity, String.class);

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choice = root.path("choices").get(0);

                // Guard against truncated responses
                String finishReason = choice.path("finish_reason").asText("");
                if ("length".equals(finishReason)) {
                    throw new RuntimeException("AI response was truncated (finish_reason=length). Increase max_tokens.");
                }

                // OpenAI-compatible response: choices[0].message.content
                String text = choice.path("message").path("content").asText();
                return stripMarkdownFences(text);

            } catch (Exception e) {
                lastException = e;
                boolean isRetryable = e.getMessage() != null &&
                        (e.getMessage().contains("503") || e.getMessage().contains("UNAVAILABLE")
                         || e.getMessage().contains("429") || e.getMessage().contains("RESOURCE_EXHAUSTED"));

                if (!isRetryable || attempt == maxAttempts) break;

                try { Thread.sleep((long) delayMs * attempt); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        throw new RuntimeException("AI API call failed: " + lastException.getMessage(), lastException);
    }

    /** Used only when ai.use.anthropic.format=true (Claude direct API). */
    private String callAnthropicApi(String systemPrompt, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", aiApiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiModel);
        body.put("max_tokens", 4096);
        body.put("system", systemPrompt);
        body.put("messages", List.of(
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(aiApiUrl, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            // Anthropic response: content[0].text
            String text = root.path("content").get(0).path("text").asText();

            return stripMarkdownFences(text);

        } catch (Exception e) {
            throw new RuntimeException("Anthropic API call failed: " + e.getMessage(), e);
        }
    }

    private String stripMarkdownFences(String text) {
        return text.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private List<AiGeneratedQuestion> parseAndSave(String rawJson, Integer userId,
                                                    Integer snippetId, String type,
                                                    int difficulty) {
        List<AiGeneratedQuestion> saved = new ArrayList<>();
        try {
            JsonNode array = objectMapper.readTree(rawJson);
            for (JsonNode node : array) {
                AiGeneratedQuestion q = new AiGeneratedQuestion();
                q.setUserId(userId);
                q.setSnippetId(snippetId);
                q.setQuestionType(type);
                q.setQuestionText(node.path("questionText").asText());
                q.setOptionA(node.path("optionA").asText());
                q.setOptionB(node.path("optionB").asText());
                q.setOptionC(node.path("optionC").asText());
                q.setOptionD(node.path("optionD").asText());
                q.setCorrectAnswer(node.path("correctAnswer").asText().toUpperCase().trim());
                q.setDifficultyLevel(difficulty);
                q.setCreatedAt(LocalDateTime.now());
                saved.add(aiQuestionRepo.save(q));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage(), e);
        }
        return saved;
    }

    private List<AiAnswerResultDTO> scoreAnswers(List<AiGeneratedQuestion> questions,
                                                  Map<Long, String> answers) {
        return questions.stream().map(q -> {
            String chosen  = answers.getOrDefault(q.getId(), "").toUpperCase().trim();
            boolean correct = q.getCorrectAnswer().equalsIgnoreCase(chosen);
            return new AiAnswerResultDTO(q.getId(), chosen, q.getCorrectAnswer(), correct);
        }).toList();
    }

    private String focusScoreToDifficultyLabel(Double score) {
        if (score == null || score < 30) return "LOW";
        if (score < 65)                  return "MEDIUM";
        return "HIGH";
    }

    private int focusScoreToLevel(Double score) {
        if (score == null || score < 30) return 1;
        if (score < 65)                  return 2;
        return 3;
    }

    private AiQuestionDTO toQuestionDTO(AiGeneratedQuestion q) {
        return new AiQuestionDTO(
                q.getId(),
                q.getQuestionText(),
                q.getOptionA(),
                q.getOptionB(),
                q.getOptionC(),
                q.getOptionD()
                // correctAnswer intentionally NOT exposed to the client
        );
    }

    // ── 5. Snippet question history ─────────────────────────────────────────────

    /**
     * Returns one entry per snippet where AI questions were generated for this user.
     * Each entry includes book + snippet info and the latest attempt result (PASSED/FAILED/null).
     */
    public List<SnippetHistoryItemDTO> getQuestionHistory() {
        Users user = currentUser();

        // Aggregate one row per snippet
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT
                  aq.snippet_id,
                  bs.snippet_title,
                  b.id          AS book_id,
                  b.title       AS book_title,
                  COUNT(aq.id)  AS question_count,
                  MIN(aq.created_at) AS created_at
                FROM ai_generated_questions aq
                JOIN book_snippets bs ON bs.id = aq.snippet_id
                JOIN books b         ON b.id  = bs.book_id
                WHERE aq.user_id = ?
                  AND aq.question_type = 'SNIPPET'
                GROUP BY aq.snippet_id, bs.snippet_title, b.id, b.title
                ORDER BY MIN(aq.created_at) DESC
                """, user.getId());

        List<SnippetHistoryItemDTO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int snippetId = ((Number) row.get("snippet_id")).intValue();

            // Look up the latest attempt outcome + score from activity logs
            Object[] details = resolveAttemptDetails(user.getId(), snippetId);

            result.add(new SnippetHistoryItemDTO(
                    snippetId,
                    (String) row.get("snippet_title"),
                    ((Number) row.get("book_id")).intValue(),
                    (String) row.get("book_title"),
                    ((Number) row.get("question_count")).intValue(),
                    (String)  details[0],
                    (Integer) details[1],
                    (Integer) details[2],
                    row.get("created_at") instanceof java.sql.Timestamp t
                            ? t.toLocalDateTime()
                            : null
            ));
        }
        return result;
    }

    /**
     * Returns [attemptResult, correctCount, totalCount].
     * attemptResult is "PASSED", "FAILED", or null.
     * correctCount/totalCount are parsed from the activity log description (e.g. "2/3 correct").
     */
    private Object[] resolveAttemptDetails(int userId, int snippetId) {
        try {
            List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
                    SELECT activity_type, activity_description FROM activity_logs
                    WHERE user_id = ?
                      AND activity_type IN ('AI_COMPREHENSION_PASSED', 'AI_COMPREHENSION_FAILED')
                      AND activity_description LIKE ?
                    ORDER BY activity_date DESC
                    LIMIT 1
                    """, userId, "%snippet " + snippetId + "%");

            if (logs.isEmpty()) return new Object[]{null, null, null};

            String type   = (String) logs.get(0).get("activity_type");
            String desc   = (String) logs.get(0).get("activity_description");
            String result = "AI_COMPREHENSION_PASSED".equals(type) ? "PASSED" : "FAILED";

            // Parse "N/M" from descriptions like:
            //   "Passed snippet 5 — 2/3 correct, +1.0 pts"
            //   "Failed snippet 5 — only 2/3 correct"
            Integer correct = null, total = null;
            if (desc != null) {
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile("(\\d+)/(\\d+)").matcher(desc);
                if (m.find()) {
                    correct = Integer.parseInt(m.group(1));
                    total   = Integer.parseInt(m.group(2));
                }
            }

            return new Object[]{result, correct, total};
        } catch (Exception e) {
            return new Object[]{null, null, null};
        }
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}