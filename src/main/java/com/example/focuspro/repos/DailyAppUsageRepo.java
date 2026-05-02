package com.example.focuspro.repos;

import com.example.focuspro.entities.DailyAppUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyAppUsageRepo extends JpaRepository<DailyAppUsage, Long> {

    Optional<DailyAppUsage> findByUserIdAndPackageNameAndUsageDate(
            int userId, String packageName, LocalDate usageDate);

    List<DailyAppUsage> findByUserIdAndUsageDateOrderByTotalMinutesDesc(
            int userId, LocalDate usageDate);

    @Transactional
    @Modifying
    @Query("DELETE FROM DailyAppUsage u WHERE u.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
