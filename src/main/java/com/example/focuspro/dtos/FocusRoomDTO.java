package com.example.focuspro.dtos;

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
    private List<RoomMemberDTO> members; // null in list view, populated in detail view
}
