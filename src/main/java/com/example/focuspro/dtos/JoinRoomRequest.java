package com.example.focuspro.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomRequest {
    private String goal; // optional, e.g. "Finishing chapter 3"
}
