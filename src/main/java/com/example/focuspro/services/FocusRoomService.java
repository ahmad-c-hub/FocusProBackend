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

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class FocusRoomService {

    @Autowired
    private FocusRoomRepo roomRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ActivityLogService activityLogService;

    // roomId -> (username -> member info)
    private final Map<Long, Map<String, RoomMemberDTO>> presence = new ConcurrentHashMap<>();

    // Invite-code alphabet – unambiguous chars only (no 0/O/1/I/l)
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String generateInviteCode() {
        return IntStream.range(0, 6)
                .mapToObj(i -> String.valueOf(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length()))))
                .collect(Collectors.joining());
    }

    private FocusRoomDTO toDto(FocusRoom room, List<RoomMemberDTO> members, boolean includeInviteCode) {
        int count = members != null ? members.size()
                : presence.getOrDefault(room.getId(), new HashMap<>()).size();
        boolean full = room.getMaxMembers() > 0 && count >= room.getMaxMembers();
        return new FocusRoomDTO(
                room.getId(),
                room.getName(),
                room.getEmoji(),
                room.getCreatedBy(),
                count,
                members,
                room.getCategory() != null ? room.getCategory() : "Study",
                room.getDescription(),
                room.getMaxMembers(),
                room.isPrivate(),
                includeInviteCode ? room.getInviteCode() : null,
                full
        );
    }

    // ── REST: list all rooms (optional category filter) ────────────────────────
    public List<FocusRoomDTO> getAllRooms(String category) {
        return roomRepo.findAll().stream()
                .filter(r -> category == null
                        || category.isBlank()
                        || "All".equalsIgnoreCase(category)
                        || category.equalsIgnoreCase(r.getCategory()))
                .map(room -> toDto(room, null, false)) // no invite code in list
                .collect(Collectors.toList());
    }

    // ── REST: get one room with full member list ────────────────────────────────
    public FocusRoomDTO getRoomById(Long id) {
        FocusRoom room = roomRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found: " + id));
        List<RoomMemberDTO> members = new ArrayList<>(
                presence.getOrDefault(id, new HashMap<>()).values());
        return toDto(room, members, true); // include invite code in detail view
    }

    // ── REST: create a room ────────────────────────────────────────────────────
    public FocusRoomDTO createRoom(CreateRoomRequest request, String creatorUsername) {
        FocusRoom room = new FocusRoom();
        room.setName(request.getName());
        room.setEmoji(request.getEmoji() != null ? request.getEmoji() : "🎯");
        room.setCreatedBy(creatorUsername);
        room.setCategory(request.getCategory() != null ? request.getCategory() : "Study");
        room.setDescription(request.getDescription());
        room.setMaxMembers(Math.max(0, request.getMaxMembers()));
        room.setPrivate(request.isPrivate());

        if (request.isPrivate()) {
            room.setInviteCode(generateInviteCode());
        }

        roomRepo.save(room);

        userRepo.findByUsername(creatorUsername).ifPresent(user ->
                activityLogService.log(user.getId(), "FOCUS_ROOM_CREATED",
                        "Created focus room: " + room.getName(),
                        String.format("{\"roomId\":%d,\"roomName\":\"%s\",\"category\":\"%s\",\"private\":%b}",
                                room.getId(), room.getName(), room.getCategory(), room.isPrivate()))
        );

        // Return with invite code so the creator can share it immediately
        return toDto(room, new ArrayList<>(), true);
    }

    // ── Presence: user joins a room ────────────────────────────────────────────
    public List<RoomMemberDTO> joinRoom(Long roomId, String username, String goal, String inviteCode) {
        FocusRoom room = roomRepo.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        // Validate invite code for private rooms
        if (room.isPrivate()) {
            if (inviteCode == null || inviteCode.isBlank()
                    || !inviteCode.trim().equalsIgnoreCase(room.getInviteCode())) {
                throw new RuntimeException("Invalid invite code");
            }
        }

        Map<String, RoomMemberDTO> roomMembers =
                presence.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());

        // Enforce capacity (allow rejoin if already present)
        if (room.getMaxMembers() > 0
                && roomMembers.size() >= room.getMaxMembers()
                && !roomMembers.containsKey(username)) {
            throw new RuntimeException("Room is full");
        }

        // Resolve display name from DB
        String displayName = username;
        Optional<Users> userOpt = userRepo.findByUsername(username);
        if (userOpt.isPresent() && userOpt.get().getName() != null) {
            displayName = userOpt.get().getName();
        }

        String joinedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        roomMembers.put(username, new RoomMemberDTO(username, displayName, goal, joinedAt));

        userOpt.ifPresent(user ->
                activityLogService.log(user.getId(), "FOCUS_ROOM_JOINED",
                        "Joined focus room: " + room.getName(),
                        String.format("{\"roomId\":%d,\"roomName\":\"%s\",\"goal\":\"%s\"}",
                                roomId, room.getName(), goal != null ? goal : ""))
        );

        return new ArrayList<>(roomMembers.values());
    }

    // ── Presence: user leaves a room ──────────────────────────────────────────
    public List<RoomMemberDTO> leaveRoom(Long roomId, String username) {
        Map<String, RoomMemberDTO> members = presence.get(roomId);
        if (members != null) {
            members.remove(username);
        }
        return members != null ? new ArrayList<>(members.values()) : new ArrayList<>();
    }

    // ── Presence: get current members ─────────────────────────────────────────
    public List<RoomMemberDTO> getMembers(Long roomId) {
        return new ArrayList<>(presence.getOrDefault(roomId, new HashMap<>()).values());
    }
}
