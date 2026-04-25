package com.example.focuspro.repos;

import com.example.focuspro.entities.DailyScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyScoreRepo extends JpaRepository<DailyScore, Long> {

    Optional<DailyScore> findByUserIdAndScoreDate(int userId, LocalDate scoreDate);

    List<DailyScore> findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
            int userId, LocalDate from, LocalDate to);
}
