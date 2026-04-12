package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "game_level_progress",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_type"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameLevelProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "game_type", nullable = false, length = 100)
    private String gameType;

    @Column(name = "max_unlocked_level", nullable = false)
    private int maxUnlockedLevel = 1;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
