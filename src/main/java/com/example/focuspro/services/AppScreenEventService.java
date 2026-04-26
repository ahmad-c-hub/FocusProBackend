package com.example.focuspro.services;

import com.example.focuspro.dtos.AppScreenEventDTO;
import com.example.focuspro.dtos.AppScreenEventRequest;
import com.example.focuspro.entities.AppScreenEvent;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.AppScreenEventRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
public class AppScreenEventService {

    @Autowired
    private AppScreenEventRepo repo;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ── Save a batch of events ────────────────────────────────────────────────

    /**
     * Receives a batch of screen events from the Flutter app and persists them
     * all in a single database round-trip via saveAll().
     * Returns the number of events successfully saved.
     */
    public int saveBatch(AppScreenEventRequest request) {
        Users user = currentUser();
        if (request.getEvents() == null || request.getEvents().isEmpty()) return 0;

        List<AppScreenEvent> entities = request.getEvents().stream()
                .filter(e -> e.getPackageName() != null && !e.getPackageName().isBlank())
                .map(e -> {
                    AppScreenEvent entity = new AppScreenEvent();
                    entity.setUserId(user.getId());
                    entity.setPackageName(e.getPackageName());
                    entity.setAppName(e.getAppName());
                    entity.setActivityName(e.getActivityName());
                    entity.setStartedAt(parseTimestamp(e.getStartedAt()));
                    entity.setRecordedAt(LocalDateTime.now());
                    return entity;
                })
                .toList();

        repo.saveAll(entities);
        return entities.size();
    }

    // ── Fetch today's events ──────────────────────────────────────────────────

    /** Returns all events for the current user from today (midnight to now). */
    public List<AppScreenEventDTO> getTodayEvents() {
        Users user = currentUser();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        return repo.findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
                        user.getId(), startOfDay, now)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /** Returns the most recent 100 events for the current user. */
    public List<AppScreenEventDTO> getRecentEvents() {
        Users user = currentUser();
        return repo.findTop100ByUserIdOrderByStartedAtDesc(user.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(raw, ISO);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private AppScreenEventDTO toDTO(AppScreenEvent e) {
        return new AppScreenEventDTO(
                e.getId(),
                e.getPackageName(),
                e.getAppName(),
                e.getActivityName(),
                e.getStartedAt(),
                e.getRecordedAt()
        );
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
