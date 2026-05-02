package com.example.focuspro.repos;

import com.example.focuspro.entities.HabitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitLogRepo extends JpaRepository<HabitLog, Integer> {

    Optional<HabitLog> findByHabitIdAndLoggedDate(int habitId, LocalDate loggedDate);

    List<HabitLog> findByHabitIdAndCompletedTrueOrderByLoggedDateDesc(int habitId);

    @Transactional
    @Modifying
    @Query("DELETE FROM HabitLog l WHERE l.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
