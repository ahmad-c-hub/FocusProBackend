package com.example.focuspro.repos;

import com.example.focuspro.entities.DailyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyScoreRepo extends JpaRepository<DailyScore, Long> {

    Optional<DailyScore> findByUserIdAndScoreDate(int userId, LocalDate scoreDate);

    List<DailyScore> findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
            int userId, LocalDate from, LocalDate to);

    @Transactional
    @Modifying
    @Query("DELETE FROM DailyScore s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
