package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "habits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 10;

    @Column(name = "frequency_per_week")
    private int frequencyPerWeek = 1;

    @Column(nullable = false)
    private boolean monday = false;

    @Column(nullable = false)
    private boolean tuesday = false;

    @Column(nullable = false)
    private boolean wednesday = false;

    @Column(nullable = false)
    private boolean thursday = false;

    @Column(nullable = false)
    private boolean friday = false;

    @Column(nullable = false)
    private boolean saturday = false;

    @Column(nullable = false)
    private boolean sunday = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
