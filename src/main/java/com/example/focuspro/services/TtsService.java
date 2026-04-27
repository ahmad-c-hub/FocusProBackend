package com.example.focuspro.services;

import com.example.focuspro.entities.TtsCacheEntry;
import com.example.focuspro.repos.TtsCacheRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private TtsCacheRepo ttsCacheRepo;

    // ── Timeout-aware HTTP client ──────────────────────────────────────────────
    private final RestTemplate rest = new RestTemplateBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(60))
            .build();

    // ── Hot in-memory layer (fastest) — cleared on restart, always checked first
    private final Map<Integer, byte[]> memCache = new ConcurrentHashMap<>();

    // ── Concurrency gate: max 2 simultaneous Google TTS calls ─────────────────
    private final Semaphore ttsGate = new Semaphore(2, true);

    private static final int MAX_ATTEMPTS = 3;

    public byte[] synthesize(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Google TTS API key not configured");
            return null;
        }

        if (text.length() > 5000) text = text.substring(0, 5000);

        int cacheKey = text.hashCode();

        // ── 1. Hot memory cache ────────────────────────────────────────────────
        byte[] cached = memCache.get(cacheKey);
        if (cached != null) {
            log.info("TTS mem-cache hit — {} bytes", cached.length);
            return cached;
        }

        // ── 2. Persistent DB cache — survives server restarts ──────────────────
        try {
            TtsCacheEntry dbEntry = ttsCacheRepo.findById(cacheKey).orElse(null);
            if (dbEntry != null) {
                log.info("TTS db-cache hit — {} bytes", dbEntry.getAudioBytes().length);
                memCache.put(cacheKey, dbEntry.getAudioBytes()); // warm mem cache
                return dbEntry.getAudioBytes();
            }
        } catch (Exception e) {
            log.warn("TTS DB cache read failed (non-fatal): {}", e.getMessage());
        }

        // ── 3. Acquire concurrency gate, then call Google TTS ─────────────────
        try {
            ttsGate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        try {
            // Re-check after acquiring — another thread may have just populated it
            cached = memCache.get(cacheKey);
            if (cached != null) {
                log.info("TTS mem-cache hit (post-wait) — {} bytes", cached.length);
                return cached;
            }

            byte[] audio = callGoogleTtsWithRetry(text, cacheKey);
            if (audio != null) {
                // Persist to DB so future server restarts don't need to re-synthesize
                try {
                    ttsCacheRepo.save(new TtsCacheEntry(cacheKey, audio));
                    log.info("TTS saved to DB cache — {} bytes", audio.length);
                } catch (Exception e) {
                    log.warn("TTS DB cache write failed (non-fatal): {}", e.getMessage());
                }
            }
            return audio;
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
                        memCache.put(cacheKey, bytes);
                        log.info("Google TTS OK (attempt {}) — {} bytes", attempt, bytes.length);
                        return bytes;
                    }
                }

                log.warn("Google TTS attempt {} returned status {}", attempt, resp.getStatusCode());

            } catch (Exception e) {
                log.error("Google TTS attempt {} failed: {}", attempt, e.getMessage());
            }

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
