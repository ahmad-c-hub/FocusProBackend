package com.example.focuspro.services;

import com.example.focuspro.dtos.CreateRoomRequest;
import com.example.focuspro.dtos.FocusRoomDTO;
import com.example.focuspro.dtos.RoomMemberDTO;
import com.example.focuspro.entities.FocusRoom;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.FocusRoomRepo;
import com.example.focuspro.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class FocusRoomService {

    @Autowired
    private FocusRoomRepo roomRepo;

    @Autowired
    private UserRepo userRepo;

    // roomId -> (username -> member info)
    // ConcurrentHashMap is thread-safe — multiple users join at the same time
    private final Map<Long, Map<String, RoomMemberDTO>> presence = new ConcurrentHashMap<>();

    // ── REST: list all rooms ───────────────────────────────────────────────
    public List<FocusRoomDTO> getAllRooms() {
        return roomRepo.findAll().stream().map(room -> {
            Map<String, RoomMemberDTO> members = presence.getOrDefault(room.getId(), new HashMap<>());
            return new FocusRoomDTO(
                    room.getId(),
                    room.getName(),
                    room.getEmoji(),
                    room.getCreatedBy(),
                    members.size(),
                    null  // don't include member details in list view
            );
        }).collect(Collectors.toList());
    }

    // ── REST: get one room with full member list ───────────────────────────
    public FocusRoomDTO getRoomById(Long id) {
        FocusRoom room = roomRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found: " + id));
        Map<String, RoomMemberDTO> members = presence.getOrDefault(id, new HashMap<>());
        return new FocusRoomDTO(
                room.getId(),
                room.getName(),
                room.getEmoji(),
                room.getCreatedBy(),
                members.size(),
                new ArrayList<>(members.values())
        );
    }

    // ── REST: create a room ────────────────────────────────────────────────
    public FocusRoomDTO createRoom(CreateRoomRequest request, String creatorUsername) {
        FocusRoom room = new FocusRoom();
        room.setName(request.getName());
        room.setEmoji(request.getEmoji() != null ? request.getEmoji() : "🎯");
        room.setCreatedBy(creatorUsername);
        roomRepo.save(room);
        return new FocusRoomDTO(room.getId(), room.getName(), room.getEmoji(),
                room.getCreatedBy(), 0, new ArrayList<>());
    }

    // ── Presence: user joins a room ────────────────────────────────────────
    public List<RoomMemberDTO> joinRoom(Long roomId, String username, String goal) {
        // Make sure the room exists
        if (!roomRepo.existsById(roomId)) {
            throw new RuntimeException("Room not found: " + roomId);
        }

        // Fetch user display name from DB
        String displayName = username;
        Optional<Users> userOpt = userRepo.findByUsername(username);
        if (userOpt.isPresent() && userOpt.get().getName() != null) {
            displayName = userOpt.get().getName();
        }

        String joinedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        RoomMemberDTO member = new RoomMemberDTO(username, displayName, goal, joinedAt);

        // presence is a ConcurrentHashMap; computeIfAbsent is atomic
        presence.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(username, member);

        return new ArrayList<>(presence.get(roomId).values());
    }

    // ── Presence: user leaves a room ──────────────────────────────────────
    public List<RoomMemberDTO> leaveRoom(Long roomId, String username) {
        Map<String, RoomMemberDTO> members = presence.get(roomId);
        if (members != null) {
            members.remove(username);
        }
        return members != null ? new ArrayList<>(members.values()) : new ArrayList<>();
    }

    // ── Presence: get current members ─────────────────────────────────────
    public List<RoomMemberDTO> getMembers(Long roomId) {
        Map<String, RoomMemberDTO> members = presence.getOrDefault(roomId, new HashMap<>());
        return new ArrayList<>(members.values());
    }
}
