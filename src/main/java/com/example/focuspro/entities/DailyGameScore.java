package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_game_scores",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyGameScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(length = 100, nullable = false)
    private String username;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(nullable = false)
    private int score;

    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @PrePersist
    void prePersist() {
        submittedAt = LocalDateTime.now();
    }
}
