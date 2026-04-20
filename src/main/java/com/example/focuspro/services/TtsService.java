package com.example.focuspro.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    @Value("${elevenlabs.api.key:}")
    private String apiKey;

    @Value("${elevenlabs.voice.id:}")
    private String voiceId;

    private final RestTemplate rest = new RestTemplate();

    public byte[] synthesize(String text, double speed) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ElevenLabs API key not configured");
            return null;
        }

        if (text.length() > 5000) text = text.substring(0, 5000);

        HttpHeaders headers = new HttpHeaders();
        headers.set("xi-api-key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "audio/mpeg");

        Map<String, Object> payload = Map.of(
                "text", text,
                "model_id", "eleven_turbo_v2",
                "voice_settings", Map.of(
                        "stability", 0.5,
                        "similarity_boost", 0.75
                )
        );

        String url = "https://api.elevenlabs.io/v1/text-to-speech/" + voiceId;
        ResponseEntity<byte[]> resp = rest.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                byte[].class
        );

        if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
            return resp.getBody();
        }
        log.warn("ElevenLabs returned status {}", resp.getStatusCode());
        return null;
    }
}
