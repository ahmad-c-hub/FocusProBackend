package com.example.focuspro.services;

import com.example.focuspro.dtos.DailyUsageRequest;
import com.example.focuspro.dtos.DailyUsageSummaryDTO;
import com.example.focuspro.entities.DailyAppUsage;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.DailyAppUsageRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
public class DailyAppUsageService {

    @Autowired
    private DailyAppUsageRepo repo;

    /**
     * Upserts today's usage totals for the current user.
     * One row per app — updates total_minutes if the row already exists.
     */
    public int upsertDailyUsage(DailyUsageRequest request) {
        Users user = currentUser();
        if (request.getUsageStats() == null || request.getUsageStats().isEmpty()) return 0;

        // Use the device's local date when provided (avoids server-UTC vs device-timezone mismatch).
        // Flutter sends ISO "yyyy-MM-dd"; fall back to server's LocalDate.now() if absent/invalid.
        LocalDate today;
        try {
            today = (request.getUsageDate() != null && !request.getUsageDate().isBlank())
                    ? LocalDate.parse(request.getUsageDate())
                    : LocalDate.now();
        } catch (DateTimeParseException e) {
            today = LocalDate.now();
        }
        LocalDateTime now = LocalDateTime.now();

        int saved = 0;
        for (DailyUsageRequest.AppUsageItem item : request.getUsageStats()) {
            if (item.getPackageName() == null || item.getPackageName().isBlank()) continue;
            if (item.getTotalMinutesToday() < 0) continue;

            Optional<DailyAppUsage> existing = repo.findByUserIdAndPackageNameAndUsageDate(
                    user.getId(), item.getPackageName(), today);

            DailyAppUsage row = existing.orElseGet(DailyAppUsage::new);
            row.setUserId(user.getId());
            row.setPackageName(item.getPackageName());
            row.setAppName(item.getAppName());
            row.setUsageDate(today);
            row.setTotalMinutes(item.getTotalMinutesToday());
            row.setUpdatedAt(now);
            repo.save(row);
            saved++;
        }
        return saved;
    }

    /**
     * Returns today's screen-time summary for the current user, most-used first.
     * Accepts the device's local date string (ISO "yyyy-MM-dd") to avoid server
     * timezone mismatch; falls back to LocalDate.now() if absent or invalid.
     */
    public List<DailyUsageSummaryDTO> getTodaySummary(String dateStr) {
        Users user = currentUser();
        LocalDate date;
        try {
            date = (dateStr != null && !dateStr.isBlank())
                    ? LocalDate.parse(dateStr)
                    : LocalDate.now();
        } catch (DateTimeParseException e) {
            date = LocalDate.now();
        }
        return repo.findByUserIdAndUsageDateOrderByTotalMinutesDesc(user.getId(), date)
                .stream()
                .map(r -> new DailyUsageSummaryDTO(
                        r.getPackageName(), r.getAppName(),
                        r.getTotalMinutes(), r.getUsageDate()))
                .toList();
    }

    private Users currentUser() {
        return (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
