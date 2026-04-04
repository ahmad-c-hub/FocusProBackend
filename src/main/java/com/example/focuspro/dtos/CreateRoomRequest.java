package com.example.focuspro.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {
    private String name;
    private String emoji; // e.g. "🔥", "📚", "🎯"
}
