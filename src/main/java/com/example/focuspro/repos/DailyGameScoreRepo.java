package com.example.focuspro.repos;

import com.example.focuspro.entities.DailyGameScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyGameScoreRepo extends JpaRepository<DailyGameScore, Long> {

    List<DailyGameScore> findByGameDateOrderByScoreDesc(LocalDate date);

    Optional<DailyGameScore> findByUserIdAndGameDate(int userId, LocalDate date);

    boolean existsByUserIdAndGameDate(int userId, LocalDate date);

    @Transactional
    @Modifying
    @Query("DELETE FROM DailyGameScore s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
