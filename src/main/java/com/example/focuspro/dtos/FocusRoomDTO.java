package com.example.focuspro.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FocusRoomDTO {
    private Long id;
    private String name;
    private String emoji;
    private String createdBy;
    private int memberCount;
    private List<RoomMemberDTO> members; // null in list view, populated in detail

    // ── New fields ────────────────────────────────────────────────────────
    private String category;
    private String description;
    private int maxMembers;          // 0 = unlimited
    @JsonProperty("isPrivate")
    private boolean isPrivate;
    private String inviteCode;       // null in list view; set after create / in detail
    private boolean isFull;          // true when memberCount >= maxMembers > 0
}
