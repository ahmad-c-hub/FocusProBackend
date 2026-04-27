package com.example.focuspro.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persistent TTS cache — stores synthesized MP3 audio keyed by the hash of the
 * input text.  Survives Render free-tier restarts unlike the in-memory map.
 */
@Entity
@Table(name = "tts_cache")
public class TtsCacheEntry {

    @Id
    @Column(name = "text_hash", nullable = false)
    private int textHash;

    @Lob
    @Column(name = "audio_bytes", nullable = false)
    private byte[] audioBytes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public TtsCacheEntry() {}

    public TtsCacheEntry(int textHash, byte[] audioBytes) {
        this.textHash  = textHash;
        this.audioBytes = audioBytes;
        this.createdAt  = LocalDateTime.now();
    }

    public int getTextHash()        { return textHash; }
    public byte[] getAudioBytes()   { return audioBytes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
