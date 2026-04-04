package com.example.focuspro.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomMemberDTO {
    private String username;
    private String displayName;   // full name
    private String goal;          // optional session goal set by the user
    private String joinedAt;      // ISO timestamp
}
