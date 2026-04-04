package com.example.focuspro.repos;

import com.example.focuspro.entities.HabitLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitLogRepo extends JpaRepository<HabitLog, Integer> {

    Optional<HabitLog> findByHabitIdAndLoggedDate(int habitId, LocalDate loggedDate);

    // Returns all completed logs for a habit, newest first — used for streak calc
    List<HabitLog> findByHabitIdAndCompletedTrueOrderByLoggedDateDesc(int habitId);
}
