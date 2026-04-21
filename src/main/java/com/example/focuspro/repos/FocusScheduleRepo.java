package com.example.focuspro.repos;

import com.example.focuspro.entities.FocusSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FocusScheduleRepo extends JpaRepository<FocusSchedule, Long> {
    List<FocusSchedule> findByUserIdAndIsActiveTrue(int userId);
    List<FocusSchedule> findByUserId(int userId);
}
