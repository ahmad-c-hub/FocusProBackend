package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "book_snippets")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BookSnippet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "book_id", nullable = false)
    private Integer bookId;

    @Column(name = "snippet_title", nullable = false, length = 255)
    private String snippetTitle;

    @Column(name = "snippet_text", nullable = false, columnDefinition = "TEXT")
    private String snippetText;

    @Column(name = "snippet_audio_url", length = 500)
    private String snippetAudioUrl;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "sequence_order")
    private Integer sequenceOrder;

    @Column(name = "created_at", updatable = false, insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;
}