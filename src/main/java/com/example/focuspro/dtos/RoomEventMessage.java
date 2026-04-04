package com.example.focuspro.dtos;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomEventMessage {
    // "JOIN" or "LEAVE"
    private String eventType;
    private String triggeredBy;         // username who caused the event
    private List<RoomMemberDTO> members; // full updated member list after the event
}
