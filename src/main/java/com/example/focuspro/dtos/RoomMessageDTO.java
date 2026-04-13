package com.example.focuspro.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomMessageDTO {
    private Long id;
    private Long roomId;
    private Integer userId;
    private String username;
    private String content;
    private String sentAt; // ISO-8601 string for easy JSON/Flutter consumption
}
