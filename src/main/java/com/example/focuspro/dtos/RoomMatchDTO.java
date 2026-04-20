package com.example.focuspro.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomMatchDTO {
    private Long roomId;
    private String roomName;
    private String roomEmoji;
    private int memberCount;
    private double matchScore;
    private String matchReason;
    private List<String> memberGoals;
    @JsonProperty("isNewRoomSuggestion")
    private boolean isNewRoomSuggestion;
}
