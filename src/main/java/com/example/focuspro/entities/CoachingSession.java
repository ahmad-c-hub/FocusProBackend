package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "coaching_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoachingSession {

    public enum SessionType { MORNING, CHECKIN, EVENING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    private SessionType sessionType;

    @Column(name = "conversation_history", columnDefinition = "TEXT")
    private String conversationHistory;

    @Column(name = "goals_snapshot", columnDefinition = "TEXT")
    private String goalsSnapshot;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
