package com.example.focuspro.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomRequest {
    private String goal;
    private String inviteCode; // required for private rooms
}
