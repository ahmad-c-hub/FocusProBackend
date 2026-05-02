package com.example.focuspro.repos;

import com.example.focuspro.entities.AppScreenEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface AppScreenEventRepo extends JpaRepository<AppScreenEvent, Long> {

    /** All events for a user between two timestamps, newest first. */
    List<AppScreenEvent> findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
            int userId, LocalDateTime from, LocalDateTime to);

    /** Most recent N events for a user regardless of date. */
    List<AppScreenEvent> findTop100ByUserIdOrderByStartedAtDesc(int userId);

    /**
     * Aggregated screen time per app for a user on a given date range.
     * Returns [packageName, appName, eventCount] — one row per unique app.
     */
    @Query("""
        SELECT e.packageName, e.appName, COUNT(e) as eventCount
        FROM AppScreenEvent e
        WHERE e.userId = :userId
          AND e.startedAt >= :from
          AND e.startedAt < :to
        GROUP BY e.packageName, e.appName
        ORDER BY eventCount DESC
    """)
    List<Object[]> countEventsByAppForUser(
            @Param("userId") int userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Transactional
    @Modifying
    @Query("DELETE FROM AppScreenEvent e WHERE e.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
