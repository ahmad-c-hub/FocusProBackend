package com.example.focuspro.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    // Lombok strips "is" from the setter name for boolean fields → Jackson
    // can't find setIsPrivate().  @JsonProperty pins the JSON key explicitly.
    @JsonProperty("isPrivate")
    private boolean isPrivate;   // generates invite code when true
}
