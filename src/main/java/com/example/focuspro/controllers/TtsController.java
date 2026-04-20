package com.example.focuspro.controllers;

import com.example.focuspro.services.TtsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/tts")
public class TtsController {

    @Autowired
    private TtsService ttsService;

    @PostMapping(produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesize(@RequestBody Map<String, Object> body) {
        String text = (String) body.getOrDefault("text", "");
        if (text.isBlank()) return ResponseEntity.badRequest().build();

        byte[] audio;
        try {
            audio = ttsService.synthesize(text);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        if (audio == null) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();

        HttpHeaders out = new HttpHeaders();
        out.setContentType(MediaType.parseMediaType("audio/mpeg"));
        out.setCacheControl(CacheControl.noCache());
        return new ResponseEntity<>(audio, out, HttpStatus.OK);
    }
}
