package com.example.focuspro.services;

import com.example.focuspro.dtos.CreateRoomRequest;
import com.example.focuspro.dtos.FocusRoomDTO;
import com.example.focuspro.dtos.RoomMatchDTO;
import com.example.focuspro.dtos.RoomMemberDTO;
import com.example.focuspro.entities.ActivityLog;
import com.example.focuspro.entities.FocusRoom;
import com.example.focuspro.entities.Habit;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.ActivityLogRepo;
import com.example.focuspro.repos.FocusRoomRepo;
import com.example.focuspro.repos.HabitRepo;
import com.example.focuspro.repos.RoomMessageRepository;
import com.example.focuspro.repos.UserRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class FocusRoomService {

    @Autowired private FocusRoomRepo roomRepo;
    @Autowired private RoomMessageRepository messageRepo;
    @Autowired private UserRepo userRepo;
    @Autowired private ActivityLogService activityLogService;
    @Autowired private AiService aiService;
    @Autowired private HabitRepo habitRepo;
    @Autowired private ActivityLogRepo activityLogRepo;

    // roomId -> (username -> member info)
    private final Map<Long, Map<String, RoomMemberDTO>> presence = new ConcurrentHashMap<>();

    // Invite-code alphabet – unambiguous chars only (no 0/O/1/I/l)
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM  = new SecureRandom();

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
                .map(room -> toDto(room, null, false)) // no invite code in list view
                .collect(Collectors.toList());
    }

    // ── REST: get one room with full member list ───────────────────────────────
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

    // ── REST: find a room by invite code ──────────────────────────────────────
    public FocusRoomDTO getRoomByInviteCode(String code) {
        return roomRepo.findAll().stream()
                .filter(r -> r.isPrivate()
                        && r.getInviteCode() != null
                        && r.getInviteCode().equalsIgnoreCase(code.trim()))
                .findFirst()
                .map(r -> toDto(r, null, false)) // don't expose the code itself
                .orElseThrow(() -> new RuntimeException("No private room found for that code"));
    }

    // ── REST: delete a room — only creator allowed ─────────────────────────────
    @Transactional
    public void deleteRoom(Long roomId, String requesterUsername) {
        FocusRoom room = roomRepo.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        if (!room.getCreatedBy().equals(requesterUsername)) {
            throw new RuntimeException("Only the room creator can delete this room");
        }

        // Evict all members from presence cache
        presence.remove(roomId);

        // Detach messages from the room (set room_id = NULL) so chat history
        // is preserved in the DB after the room is deleted.
        messageRepo.detachFromRoom(roomId);
        roomRepo.delete(room);
    }

    // ── Presence: user joins a room ────────────────────────────────────────────
    @Transactional
    public List<RoomMemberDTO> joinRoom(Long roomId, String username, String goal, String inviteCode) {
        FocusRoom room = roomRepo.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        // Validate invite code for private rooms — creator can always re-join freely
        if (room.isPrivate() && !username.equals(room.getCreatedBy())) {
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

        // ── Track last activity so the scheduler knows this room is alive ──────
        room.setLastActivityAt(LocalDateTime.now());
        roomRepo.save(room);

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

    // ── Smart Matching ────────────────────────────────────────────────────────
    public List<RoomMatchDTO> findMatchForUser(String sessionGoal, Users currentUser) {
        // a) Get all active room IDs (at least 1 member present)
        List<Long> activeRoomIds = presence.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // c) Load current user's habits
        List<Habit> habits = habitRepo.findByUserId(currentUser.getId());
        String habitTitles = habits.isEmpty()
                ? "none"
                : habits.stream().map(Habit::getTitle).collect(Collectors.joining(", "));

        // d) Load last 20 FOCUS_ROOM_JOINED logs to derive typical study hours
        List<ActivityLog> joinLogs = activityLogRepo
                .findTop20ByUserIdAndActivityTypeOrderByActivityDateDesc(
                        currentUser.getId(), "FOCUS_ROOM_JOINED");

        String studyHoursDescription;
        if (joinLogs.isEmpty()) {
            studyHoursDescription = "no history yet";
        } else {
            Map<Integer, Long> hourCounts = joinLogs.stream()
                    .filter(l -> l.getActivityDate() != null)
                    .collect(Collectors.groupingBy(
                            l -> l.getActivityDate().getHour(),
                            Collectors.counting()));
            studyHoursDescription = hourCounts.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(3)
                    .map(e -> String.format("%d:00-%d:00 (%d sessions)", e.getKey(), e.getKey() + 1, e.getValue()))
                    .collect(Collectors.joining(", "));
        }

        // Handle no active rooms
        if (activeRoomIds.isEmpty()) {
            activityLogService.log(currentUser.getId(), "FOCUS_ROOM_MATCH_REQUESTED", sessionGoal);
            RoomMatchDTO suggestion = buildNewRoomSuggestion(
                    "No active rooms right now. Create a new focused room for: " + sessionGoal);
            return List.of(suggestion);
        }

        // b) Collect member goals and room entities for each active room
        StringBuilder roomsBlock = new StringBuilder();
        Map<Long, List<String>> roomGoalsMap = new HashMap<>();
        Map<Long, FocusRoom> roomEntityMap = new HashMap<>();

        for (Long roomId : activeRoomIds) {
            FocusRoom room = roomRepo.findById(roomId).orElse(null);
            if (room == null) continue;
            roomEntityMap.put(roomId, room);

            Map<String, RoomMemberDTO> members = presence.getOrDefault(roomId, new HashMap<>());
            List<String> goals = members.values().stream()
                    .map(RoomMemberDTO::getGoal)
                    .filter(g -> g != null && !g.isBlank())
                    .collect(Collectors.toList());
            roomGoalsMap.put(roomId, goals);

            roomsBlock.append(String.format(
                    "Room ID: %d, Name: \"%s\", Members: %d, Goals: [%s]\n",
                    roomId, room.getName(), members.size(),
                    goals.isEmpty() ? "no goals set" : String.join("; ", goals)));
        }

        if (roomEntityMap.isEmpty()) {
            activityLogService.log(currentUser.getId(), "FOCUS_ROOM_MATCH_REQUESTED", sessionGoal);
            return List.of(buildNewRoomSuggestion(
                    "No suitable rooms found. Create a new room for: " + sessionGoal));
        }

        // e) Build AI prompt
        String systemPrompt = """
                You are a focus room matching AI inside FocusPro, a productivity app.
                Analyze which focus room best matches the user's session goal, habits, and study time patterns.
                Consider semantic similarity of goals — not just keyword matching.
                Return ONLY valid JSON — no markdown fences, no explanation, no extra text.
                """;

        String userPrompt = String.format("""
                User session goal: "%s"
                User habit titles: %s
                User's typical study hours: %s

                Available rooms:
                %s

                Return a JSON array ranked by match quality. Each object must have exactly these fields:
                {
                  "roomId": <number>,
                  "matchScore": <number between 0 and 100>,
                  "matchReason": "<one sentence explaining why this room matches or doesn't match>"
                }

                Rules:
                - Compare the user's goal semantically against room member goals
                - Factor in time pattern overlap if the user has study history
                - Factor in habit title overlap with room focus topics
                - If no room scores above 40, still return all rooms but set the lowest-scoring one's matchReason to explain why the user should create a new room
                - Sort descending by matchScore
                - Return ONLY the JSON array, nothing else
                """,
                sessionGoal,
                habitTitles,
                studyHoursDescription,
                roomsBlock.toString());

        // f) Call AI
        String rawJson = aiService.callAiApiPublic(systemPrompt, userPrompt);

        // g) Parse AI response and merge with room data
        ObjectMapper mapper = new ObjectMapper();
        List<RoomMatchDTO> results = new ArrayList<>();
        try {
            JsonNode array = mapper.readTree(rawJson);
            boolean anyAbove40 = false;

            for (JsonNode node : array) {
                long roomId = node.path("roomId").asLong();
                double score = node.path("matchScore").asDouble();
                String reason = node.path("matchReason").asText();

                FocusRoom room = roomEntityMap.get(roomId);
                if (room == null) continue;

                if (score >= 40) anyAbove40 = true;

                Map<String, RoomMemberDTO> members = presence.getOrDefault(roomId, new HashMap<>());

                RoomMatchDTO dto = new RoomMatchDTO();
                dto.setRoomId(roomId);
                dto.setRoomName(room.getName());
                dto.setRoomEmoji(room.getEmoji());
                dto.setMemberCount(members.size());
                dto.setMatchScore(score);
                dto.setMatchReason(reason);
                dto.setMemberGoals(roomGoalsMap.getOrDefault(roomId, new ArrayList<>()));
                dto.setNewRoomSuggestion(false);
                results.add(dto);
            }

            // If no room scored above 40, prepend a "create new room" suggestion
            if (!anyAbove40 && !results.isEmpty()) {
                String reason = results.get(0).getMatchReason();
                RoomMatchDTO newRoom = buildNewRoomSuggestion(
                        "None of the active rooms closely match your goal. " +
                        "Consider creating a new room. Closest match note: " + reason);
                results.add(0, newRoom);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI matching response: " + e.getMessage(), e);
        }

        if (results.isEmpty()) {
            results.add(buildNewRoomSuggestion(
                    "No matching rooms found for your goal. Create a focused room to find your tribe."));
        }

        // h) Sort by matchScore descending (new room suggestion always leads if present)
        results.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));

        // i) Log the match request
        activityLogService.log(currentUser.getId(), "FOCUS_ROOM_MATCH_REQUESTED", sessionGoal);

        return results;
    }

    private RoomMatchDTO buildNewRoomSuggestion(String reason) {
        RoomMatchDTO dto = new RoomMatchDTO();
        dto.setRoomId(null);
        dto.setRoomName("Create a new room");
        dto.setRoomEmoji("✨");
        dto.setMemberCount(0);
        dto.setMatchScore(100.0);
        dto.setMatchReason(reason);
        dto.setMemberGoals(new ArrayList<>());
        dto.setNewRoomSuggestion(true);
        return dto;
    }

    // ── Scheduled: auto-delete rooms inactive for 2+ days ─────────────────────
    // Runs every day at 03:00 server time
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeInactiveRooms() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(2);
        List<FocusRoom> stale = roomRepo.findInactiveRooms(cutoff);
        if (stale.isEmpty()) return;

        for (FocusRoom room : stale) {
            // Only delete if nobody is currently in the room
            Map<String, RoomMemberDTO> current = presence.get(room.getId());
            if (current != null && !current.isEmpty()) continue;

            presence.remove(room.getId());
            messageRepo.detachFromRoom(room.getId());
            roomRepo.delete(room);
        }
    }
}
