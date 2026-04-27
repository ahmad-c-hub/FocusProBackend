package com.example.focuspro.repos;

import com.example.focuspro.entities.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameResultRepo extends JpaRepository<GameResult, Long> {

    List<GameResult> findByUserIdOrderByPlayedAtDesc(int userId);

    /** How many distinct game types this user has ever played. */
    @Query("SELECT COUNT(DISTINCT r.gameId) FROM GameResult r WHERE r.userId = :userId")
    int countDistinctGamesByUserId(@Param("userId") int userId);
}
