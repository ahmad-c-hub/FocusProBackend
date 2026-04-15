package com.example.focuspro.repos;

import com.example.focuspro.entities.DailyGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyGoalRepo extends JpaRepository<DailyGoal, Long> {

    List<DailyGoal> findByUserIdAndGoalDate(int userId, LocalDate goalDate);

    List<DailyGoal> findByUserIdAndGoalDateAndStatus(int userId, LocalDate goalDate, DailyGoal.Status status);
}
