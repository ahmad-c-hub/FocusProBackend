package com.example.focuspro.repos;

import com.example.focuspro.entities.GameLevelProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface GameLevelProgressRepo extends JpaRepository<GameLevelProgress, Long> {

    Optional<GameLevelProgress> findByUserIdAndGameType(int userId, String gameType);

    List<GameLevelProgress> findByUserId(int userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM GameLevelProgress p WHERE p.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
