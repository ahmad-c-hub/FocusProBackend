package com.example.focuspro.repos;

import com.example.focuspro.entities.DailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyChallengeRepo extends JpaRepository<DailyChallenge, Long> {

    Optional<DailyChallenge> findByUserIdAndChallengeDate(int userId, LocalDate date);

    List<DailyChallenge> findByUserIdOrderByChallengeDateDesc(int userId);

    Optional<DailyChallenge> findFirstByUserIdAndUserWeaknessHintIsNotNullOrderByChallengeDateDesc(int userId);
}
