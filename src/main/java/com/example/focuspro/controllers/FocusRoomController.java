package com.example.focuspro.controllers;

import com.example.focuspro.dtos.CreateRoomRequest;
import com.example.focuspro.dtos.FocusRoomDTO;
import com.example.focuspro.dtos.JoinRoomRequest;
import com.example.focuspro.dtos.MatchRequest;
import com.example.focuspro.dtos.RoomMatchDTO;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.UserRepo;
import com.example.focuspro.services.FocusRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rooms")
public class FocusRoomController {

    @Autowired
    private FocusRoomService roomService;

    @Autowired
    private UserRepo userRepo;

    // GET /rooms — list all rooms (optional ?category=Study filter)
    @GetMapping
    public ResponseEntity<List<FocusRoomDTO>> getAllRooms(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(roomService.getAllRooms(category));
    }

    // GET /rooms/{id} — full room detail with member list
    @GetMapping("/{id}")
    public ResponseEntity<FocusRoomDTO> getRoom(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    // GET /rooms/by-code/{code} — look up a private room by its invite code
    @GetMapping("/by-code/{code}")
    public ResponseEntity<?> getRoomByCode(@PathVariable String code) {
        try {
            return ResponseEntity.ok(roomService.getRoomByInviteCode(code));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // POST /rooms — create a new room
    @PostMapping
    public ResponseEntity<FocusRoomDTO> createRoom(
            @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(roomService.createRoom(request, userDetails.getUsername()));
    }

    // POST /rooms/{id}/join — join a room (validates invite code & capacity)
    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinRoom(
            @PathVariable Long id,
            @RequestBody(required = false) JoinRoomRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            roomService.joinRoom(
                    id,
                    userDetails.getUsername(),
                    request != null ? request.getGoal() : null,
                    request != null ? request.getInviteCode() : null);

            return ResponseEntity.ok(roomService.getRoomById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // POST /rooms/{id}/leave
    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        roomService.leaveRoom(id, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    // POST /rooms/match — AI smart matching
    @PostMapping("/match")
    public ResponseEntity<?> matchRoom(
            @RequestBody MatchRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Users user = userRepo.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            List<RoomMatchDTO> results = roomService.findMatchForUser(request.getSessionGoal(), user);
            return ResponseEntity.ok(results);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("AI API call failed")) {
                return ResponseEntity.status(503)
                        .body(Map.of("error", "AI matching service temporarily unavailable. Please try again."));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // DELETE /rooms/{id} — only the room creator is allowed
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoom(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            roomService.deleteRoom(id, userDetails.getUsername());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
