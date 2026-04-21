package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "challenge_type", nullable = false, length = 20)
    private String challengeType; // GAME / BOOK / CUSTOM

    @Column(name = "target_game_type", length = 100)
    private String targetGameType;

    @Column(name = "target_book_id")
    private Integer targetBookId;

    @Column(name = "challenge_title", length = 255)
    private String challengeTitle;

    @Column(name = "challenge_description", columnDefinition = "TEXT")
    private String challengeDescription;

    @Column(name = "weakness_area", length = 50)
    private String weaknessArea;

    @Column(name = "user_weakness_hint", columnDefinition = "TEXT")
    private String userWeaknessHint;

    @Column(name = "challenge_date", nullable = false)
    private LocalDate challengeDate;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
