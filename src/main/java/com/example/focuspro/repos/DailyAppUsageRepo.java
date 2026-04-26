package com.example.focuspro.repos;

import com.example.focuspro.entities.DailyAppUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyAppUsageRepo extends JpaRepository<DailyAppUsage, Long> {

    Optional<DailyAppUsage> findByUserIdAndPackageNameAndUsageDate(
            int userId, String packageName, LocalDate usageDate);

    List<DailyAppUsage> findByUserIdAndUsageDateOrderByTotalMinutesDesc(
            int userId, LocalDate usageDate);
}
