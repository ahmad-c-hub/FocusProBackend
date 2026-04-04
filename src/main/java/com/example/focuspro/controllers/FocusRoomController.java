package com.example.focuspro.controllers;

import com.example.focuspro.dtos.CreateRoomRequest;
import com.example.focuspro.dtos.FocusRoomDTO;
import com.example.focuspro.dtos.JoinRoomRequest;
import com.example.focuspro.dtos.RoomMemberDTO;
import com.example.focuspro.entities.FocusRoom;
import com.example.focuspro.repos.FocusRoomRepo;
import com.example.focuspro.services.FocusRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms")
public class FocusRoomController {

    @Autowired
    private FocusRoomService roomService;

    @Autowired
    private FocusRoomRepo roomRepo;

    // GET /rooms — list all rooms (with live member count)
    @GetMapping
    public ResponseEntity<List<FocusRoomDTO>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    // GET /rooms/{id} — full room detail with member list
    @GetMapping("/{id}")
    public ResponseEntity<FocusRoomDTO> getRoom(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    // POST /rooms — create a new room
    @PostMapping
    public ResponseEntity<FocusRoomDTO> createRoom(
            @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(roomService.createRoom(request, userDetails.getUsername()));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<FocusRoomDTO> joinRoom(
            @PathVariable Long id,
            @RequestBody JoinRoomRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<RoomMemberDTO> members = roomService.joinRoom(
                id, userDetails.getUsername(),
                request != null ? request.getGoal() : null);
        FocusRoom room = roomRepo.findById(id).orElseThrow();
        return ResponseEntity.ok(new FocusRoomDTO(
                room.getId(), room.getName(), room.getEmoji(),
                room.getCreatedBy(), members.size(), members));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        roomService.leaveRoom(id, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}
