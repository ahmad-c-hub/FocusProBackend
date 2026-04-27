package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_scores",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "score_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "score_date", nullable = false)
    private LocalDate scoreDate;

    @Column(name = "total_points", nullable = false)
    private double totalPoints;

    /** Points deducted for distracting-app screen time today. Updated on every sync. */
    @Column(name = "screen_penalty", nullable = false)
    private double screenPenalty = 0.0;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    /** Effective score after subtracting the screen-time penalty (never below 0). */
    public double getEffectivePoints() {
        return Math.max(0.0, totalPoints - screenPenalty);
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        lastUpdated = LocalDateTime.now();
    }
}
