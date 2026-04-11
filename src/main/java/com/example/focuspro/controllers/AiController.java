package com.example.focuspro.controllers;

import com.example.focuspro.dtos.*;
import com.example.focuspro.services.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    // ── Snippet comprehension ─────────────────────────────────────────────────

    /**
     * Called right after the user finishes reading a snippet.
     * Returns 3 AI-generated questions specific to that snippet + the user's level.
     * Questions are cached — safe to call multiple times.
     *
     * GET /ai/snippet/{snippetId}/questions
     */
    @GetMapping("/snippet/{snippetId}/questions")
    public List<AiQuestionDTO> getSnippetQuestions(@PathVariable Integer snippetId) {
        return aiService.generateSnippetQuestions(snippetId);
    }

    /**
     * User submits their answers to the snippet comprehension check.
     * Body: { "answers": { "<questionId>": "A", "<questionId>": "C", ... } }
     * On pass  → snippet marked complete, focus score awarded.
     * On fail  → snippet stays incomplete, no score.
     *
     * POST /ai/snippet/{snippetId}/submit
     */
    @PostMapping("/snippet/{snippetId}/submit")
    public SnippetCheckResponse submitSnippetAnswers(
            @PathVariable Integer snippetId,
            @RequestBody AiAnswerRequest request) {
        return aiService.submitSnippetAnswers(snippetId, request.getAnswers());
    }

    // ── Retention audit ───────────────────────────────────────────────────────

    /**
     * Generates a retention test from the user's past completed snippets.
     * Flutter should call this periodically (e.g. every 5 completed snippets).
     * Returns freshly generated questions — never cached, so the user can't memorise them.
     *
     * GET /ai/retention/generate
     */
    @GetMapping("/retention/generate")
    public List<AiQuestionDTO> generateRetentionTest() {
        return aiService.generateRetentionTest();
    }

    /**
     * User submits answers to the retention audit.
     * Body: { "answers": { "<questionId>": "B", ... } }
     * Score >= 66% → +1.0 focus pts
     * Score 33-66% → no change
     * Score < 33%  → -1.5 focus pts (score can drop)
     *
     * POST /ai/retention/submit
     */
    @PostMapping("/retention/submit")
    public RetentionTestResponse submitRetentionTest(@RequestBody AiAnswerRequest request) {
        return aiService.submitRetentionTest(request.getAnswers());
    }
}
