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

        LocalDate today = LocalDate.now();
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

    /** Returns today's screen-time summary for the current user, most-used first. */
    public List<DailyUsageSummaryDTO> getTodaySummary() {
        Users user = currentUser();
        return repo.findByUserIdAndUsageDateOrderByTotalMinutesDesc(user.getId(), LocalDate.now())
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
