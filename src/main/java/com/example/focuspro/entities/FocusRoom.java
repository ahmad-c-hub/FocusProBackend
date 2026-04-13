package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "focus_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FocusRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 10)
    private String emoji;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ── New fields ────────────────────────────────────────────────────────────

    /** Category of the room (Study, Work, Creative, Fitness, Mindfulness, Gaming, Research, Other) */
    @Column(nullable = false, length = 50)
    private String category = "Study";

    /** Short optional description of the room's focus topic */
    @Column(length = 200)
    private String description;

    /** Maximum number of members allowed. 0 = unlimited. */
    @Column(name = "max_members", nullable = false)
    private int maxMembers = 0;

    /** Whether the room requires an invite code to join */
    @Column(name = "is_private", nullable = false)
    private boolean isPrivate = false;

    /** Auto-generated 6-char alphanumeric invite code for private rooms */
    @Column(name = "invite_code", length = 10)
    private String inviteCode;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (category == null) category = "Study";
    }
}
