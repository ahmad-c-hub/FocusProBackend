package com.example.focuspro.repos;

import com.example.focuspro.entities.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface HabitRepo extends JpaRepository<Habit, Integer> {

    List<Habit> findByUserId(int userId);

    Optional<Habit> findByIdAndUserId(int id, int userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM Habit h WHERE h.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
