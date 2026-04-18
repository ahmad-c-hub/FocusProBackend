package com.example.focuspro.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/tts")
public class TtsController {

    private static final Logger log = LoggerFactory.getLogger(TtsController.class);
    private static final String VOICE_ID = "21m00Tcm4TlvDq8ikWAM"; // Rachel
    private static final String ELEVENLABS_URL =
            "https://api.elevenlabs.io/v1/text-to-speech/" + VOICE_ID;

    @Value("${elevenlabs.api.key:}")
    private String apiKey;

    private final RestTemplate rest = new RestTemplate();

    @PostMapping(produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesize(@RequestBody Map<String, Object> body) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ElevenLabs API key not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        String text = (String) body.getOrDefault("text", "");
        if (text.isBlank()) return ResponseEntity.badRequest().build();

        // ElevenLabs caps per request — trim if needed
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

        try {
            ResponseEntity<byte[]> resp = rest.exchange(
                    ELEVENLABS_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    byte[].class
            );

            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                HttpHeaders out = new HttpHeaders();
                out.setContentType(MediaType.parseMediaType("audio/mpeg"));
                out.setCacheControl(CacheControl.noCache());
                return new ResponseEntity<>(resp.getBody(), out, HttpStatus.OK);
            }
            log.warn("ElevenLabs returned status {}", resp.getStatusCode());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (Exception e) {
            log.error("ElevenLabs TTS error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
