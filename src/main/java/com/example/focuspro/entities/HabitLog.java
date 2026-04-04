package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "habit_logs",
    uniqueConstraints = @UniqueConstraint(
        name = "habit_logs_habit_id_logged_date_key",
        columnNames = {"habit_id", "logged_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HabitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "habit_id", nullable = false)
    private int habitId;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "logged_date", nullable = false)
    private LocalDate loggedDate;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "time_spent_minutes")
    private Integer timeSpentMinutes;

    @Column(name = "logged_at")
    private LocalDateTime loggedAt;

    @PrePersist
    protected void onCreate() {
        loggedAt = LocalDateTime.now();
    }
}
