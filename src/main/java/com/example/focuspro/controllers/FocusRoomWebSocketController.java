package com.example.focuspro.controllers;

import com.example.focuspro.dtos.JoinRoomRequest;
import com.example.focuspro.dtos.RoomEventMessage;
import com.example.focuspro.dtos.RoomMemberDTO;
import com.example.focuspro.services.FocusRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

@Controller
public class FocusRoomWebSocketController {

    @Autowired
    private FocusRoomService roomService;

    // SimpMessagingTemplate lets us push messages to any topic from inside the server
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Client sends to: /app/room/{roomId}/join
     * Payload: { "goal": "Finishing chapter 3" }   (goal is optional)
     *
     * Server broadcasts to: /topic/room/{roomId}
     * Payload: { "eventType": "JOIN", "triggeredBy": "...", "members": [...] }
     */
    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(
            @DestinationVariable Long roomId,
            @Payload JoinRoomRequest request,
            Principal principal) {

        if (principal == null) return; // unauthenticated — ignore

        String username = principal.getName();
        List<RoomMemberDTO> updatedMembers = roomService.joinRoom(roomId, username,
                request != null ? request.getGoal() : null,
                request != null ? request.getInviteCode() : null);

        RoomEventMessage event = new RoomEventMessage("JOIN", username, updatedMembers);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, event);
    }

    /**
     * Client sends to: /app/room/{roomId}/leave
     * No payload needed.
     *
     * Server broadcasts to: /topic/room/{roomId}
     * Payload: { "eventType": "LEAVE", "triggeredBy": "...", "members": [...] }
     */
    @MessageMapping("/room/{roomId}/leave")
    public void leaveRoom(
            @DestinationVariable Long roomId,
            Principal principal) {

        if (principal == null) return; // unauthenticated — ignore

        String username = principal.getName();
        List<RoomMemberDTO> updatedMembers = roomService.leaveRoom(roomId, username);

        RoomEventMessage event = new RoomEventMessage("LEAVE", username, updatedMembers);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, event);
    }
}
