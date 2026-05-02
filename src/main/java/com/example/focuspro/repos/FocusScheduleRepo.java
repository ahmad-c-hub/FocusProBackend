package com.example.focuspro.repos;

import com.example.focuspro.entities.FocusSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface FocusScheduleRepo extends JpaRepository<FocusSchedule, Long> {
    List<FocusSchedule> findByUserIdAndIsActiveTrue(int userId);
    List<FocusSchedule> findByUserId(int userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM FocusSchedule s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
