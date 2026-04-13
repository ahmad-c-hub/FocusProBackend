package com.example.focuspro.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {
    private String name;
    private String emoji;

    // ── New fields ────────────────────────────────────────────────────────
    private String category;     // defaults to "Study" if null
    private String description;  // optional, up to 200 chars
    private int maxMembers;      // 0 = unlimited
    private boolean isPrivate;   // generates invite code when true
}
