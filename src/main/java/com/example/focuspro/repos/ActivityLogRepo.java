package com.example.focuspro.repos;

import com.example.focuspro.entities.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepo extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByUserIdOrderByActivityDateDesc(int userId);

    List<ActivityLog> findTop20ByUserIdAndActivityTypeOrderByActivityDateDesc(int userId, String activityType);

    /** Real count of all activity-log entries matching a given type — no artificial cap. */
    int countByUserIdAndActivityType(int userId, String activityType);

    /** Count entries of a given type on or after startOfDay (used for today-only stats). */
    @Query("SELECT COUNT(l) FROM ActivityLog l WHERE l.userId = :userId AND l.activityType = :type AND l.activityDate >= :startOfDay")
    int countByUserIdAndActivityTypeToday(@Param("userId") int userId, @Param("type") String type, @Param("startOfDay") LocalDateTime startOfDay);

    @Transactional
    @Modifying
    @Query("DELETE FROM ActivityLog l WHERE l.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
