package com.example.focuspro.repos;

import com.example.focuspro.entities.DailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyChallengeRepo extends JpaRepository<DailyChallenge, Long> {

    Optional<DailyChallenge> findByUserIdAndChallengeDate(int userId, LocalDate date);

    List<DailyChallenge> findByUserIdOrderByChallengeDateDesc(int userId);

    Optional<DailyChallenge> findFirstByUserIdAndUserWeaknessHintIsNotNullOrderByChallengeDateDesc(int userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM DailyChallenge c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
