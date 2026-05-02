package com.example.focuspro.repos;

import com.example.focuspro.entities.DailyGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface DailyGoalRepo extends JpaRepository<DailyGoal, Long> {

    List<DailyGoal> findByUserIdAndGoalDate(int userId, LocalDate goalDate);

    List<DailyGoal> findByUserIdAndGoalDateAndStatus(int userId, LocalDate goalDate, DailyGoal.Status status);

    @Transactional
    @Modifying
    @Query("DELETE FROM DailyGoal g WHERE g.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
