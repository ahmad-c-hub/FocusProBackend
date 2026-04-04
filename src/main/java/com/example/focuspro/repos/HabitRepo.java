package com.example.focuspro.repos;

import com.example.focuspro.entities.Habit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HabitRepo extends JpaRepository<Habit, Integer> {

    List<Habit> findByUserId(int userId);

    Optional<Habit> findByIdAndUserId(int id, int userId);
}
