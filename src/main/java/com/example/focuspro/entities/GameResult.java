package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "game_id", nullable = false)
    private int gameId;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(nullable = false)
    private int score;

    @Column(name = "time_played_seconds", nullable = false)
    private int timePlayedSeconds;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "played_at")
    private LocalDateTime playedAt;

    @Column(name = "focus_score_gained")
    private double focusScoreGained;
}
