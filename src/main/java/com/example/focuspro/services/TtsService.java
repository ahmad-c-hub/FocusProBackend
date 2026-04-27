package com.example.focuspro.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    @Value("${google.tts.api.key:}")
    private String apiKey;

    // ── Timeout-aware HTTP client ──────────────────────────────────────────────
    // connect: 10 s, read: 60 s  – prevents stuck threads on the free tier
    private final RestTemplate rest = new RestTemplateBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(60))
            .build();

    // ── In-memory audio cache (hash → MP3 bytes) ───────────────────────────────
    private final Map<Integer, byte[]> audioCache = new ConcurrentHashMap<>();

    // ── Concurrency gate ───────────────────────────────────────────────────────
    // Limit simultaneous Google TTS calls to 2.  Without this, parallel chapter
    // prefetches all hit Google at once, trigger rate-limiting, and every one
    // returns null → 503 to the client.
    private final Semaphore ttsGate = new Semaphore(2, true);

    private static final int MAX_ATTEMPTS = 3;

    public byte[] synthesize(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Google TTS API key not configured");
            return null;
        }

        if (text.length() > 5000) text = text.substring(0, 5000);

        int cacheKey = text.hashCode();
        byte[] cached = audioCache.get(cacheKey);
        if (cached != null) {
            log.info("TTS cache hit — {} bytes", cached.length);
            return cached;
        }

        // ── Acquire gate (blocks rather than pile-driving Google) ──────────────
        try {
            ttsGate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        try {
            // Re-check cache: another thread may have populated it while we waited
            cached = audioCache.get(cacheKey);
            if (cached != null) {
                log.info("TTS cache hit (post-wait) — {} bytes", cached.length);
                return cached;
            }

            return callGoogleTtsWithRetry(text, cacheKey);
        } finally {
            ttsGate.release();
        }
    }

    // ── Internal: call Google TTS with exponential back-off retries ────────────
    private byte[] callGoogleTtsWithRetry(String text, int cacheKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
                "input", Map.of("text", text),
                "voice", Map.of(
                        "languageCode", "en-US",
                        "name", "en-US-Neural2-D",
                        "ssmlGender", "MALE"
                ),
                "audioConfig", Map.of(
                        "audioEncoding", "MP3",
                        "speakingRate", 1.0
                )
        );

        String url = "https://texttospeech.googleapis.com/v1/text:synthesize?key=" + apiKey;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<Map> resp = rest.exchange(
                        url,
                        HttpMethod.POST,
                        new HttpEntity<>(payload, headers),
                        Map.class
                );

                if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                    String audioContent = (String) resp.getBody().get("audioContent");
                    if (audioContent != null) {
                        byte[] bytes = Base64.getDecoder().decode(audioContent);
                        audioCache.put(cacheKey, bytes);
                        log.info("Google TTS OK (attempt {}) — {} bytes", attempt, bytes.length);
                        return bytes;
                    }
                }

                log.warn("Google TTS attempt {} returned status {}", attempt, resp.getStatusCode());

            } catch (Exception e) {
                log.error("Google TTS attempt {} failed: {}", attempt, e.getMessage());
            }

            // Exponential back-off: 1 s, 2 s, 4 s … (skip after last attempt)
            if (attempt < MAX_ATTEMPTS) {
                long backoffMs = (long) Math.pow(2, attempt - 1) * 1000L;
                log.info("TTS retry in {} ms (attempt {} of {})", backoffMs, attempt, MAX_ATTEMPTS);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        log.error("Google TTS failed after {} attempts", MAX_ATTEMPTS);
        return null;
    }
}
