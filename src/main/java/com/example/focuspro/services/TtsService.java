package com.example.focuspro.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    @Value("${google.tts.api.key:}")
    private String apiKey;

    private final RestTemplate rest = new RestTemplate();

    public byte[] synthesize(String text, double speed) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Google TTS API key not configured");
            return null;
        }

        if (text.length() > 5000) text = text.substring(0, 5000);

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
                        "speakingRate", Math.max(0.25, Math.min(4.0, speed))
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
                log.info("Google TTS OK — {} bytes", bytes.length);
                return bytes;
            }
        }
        log.warn("Google TTS returned status {}", resp.getStatusCode());
        return null;
    }
}
