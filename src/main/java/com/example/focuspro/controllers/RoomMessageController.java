package com.example.focuspro.controllers;

import com.example.focuspro.dtos.RoomMessageDTO;
import com.example.focuspro.dtos.SendMessageRequest;
import com.example.focuspro.entities.RoomMessage;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.RoomMessageRepository;
import com.example.focuspro.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/rooms/{roomId}/messages")
public class RoomMessageController {

    @Autowired
    private RoomMessageRepository messageRepo;

    @Autowired
    private UserRepo userRepo;

    // GET /rooms/{roomId}/messages?after={iso-timestamp}
    // Returns all messages after the timestamp (for incremental polling),
    // or the last 50 if no `after` param is provided.
    @GetMapping
    public ResponseEntity<List<RoomMessageDTO>> getMessages(
            @PathVariable Long roomId,
            @RequestParam(required = false) String after) {

        List<RoomMessage> messages;
        if (after != null && !after.isBlank()) {
            Instant afterInstant = Instant.parse(after);
            messages = messageRepo.findByRoomIdAndSentAtAfterOrderBySentAtAsc(roomId, afterInstant);
        } else {
            messages = messageRepo.findTop50ByRoomIdOrderBySentAtAsc(roomId);
        }

        return ResponseEntity.ok(messages.stream().map(this::toDto).toList());
    }

    // POST /rooms/{roomId}/messages
    // Saves a new message from the authenticated user and returns the saved DTO.
    @PostMapping
    public ResponseEntity<RoomMessageDTO> sendMessage(
            @PathVariable Long roomId,
            @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Users user = userRepo.findByUsername(userDetails.getUsername()).orElseThrow();

        RoomMessage msg = new RoomMessage();
        msg.setRoomId(roomId);
        msg.setUserId(user.getId());
        msg.setUsername(user.getUsername());
        msg.setContent(request.getContent());

        RoomMessage saved = messageRepo.save(msg);
        return ResponseEntity.ok(toDto(saved));
    }

    private RoomMessageDTO toDto(RoomMessage m) {
        return new RoomMessageDTO(
                m.getId(),
                m.getRoomId(),
                m.getUserId(),
                m.getUsername(),
                m.getContent(),
                m.getSentAt().toString()
        );
    }
}
