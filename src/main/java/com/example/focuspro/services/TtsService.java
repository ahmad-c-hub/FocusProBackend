package com.example.focuspro.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    @Value("${google.tts.api.key:}")
    private String apiKey;

    private final RestTemplate rest = new RestTemplate();
    private final Map<Integer, byte[]> audioCache = new ConcurrentHashMap<>();

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
        ResponseEntity<Map> resp;
        try {
            resp = rest.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );
        } catch (Exception e) {
            log.error("Google TTS request failed: {}", e.getMessage());
            return null;
        }

        if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
            String audioContent = (String) resp.getBody().get("audioContent");
            if (audioContent != null) {
                byte[] bytes = Base64.getDecoder().decode(audioContent);
                audioCache.put(cacheKey, bytes);
                log.info("Google TTS OK — {} bytes cached", bytes.length);
                return bytes;
            }
        }
        log.warn("Google TTS returned status {}", resp.getStatusCode());
        return null;
    }
}
