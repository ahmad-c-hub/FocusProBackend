package com.example.focuspro.repos;

import com.example.focuspro.entities.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GameResultRepo extends JpaRepository<GameResult, Long> {

    List<GameResult> findByUserIdOrderByPlayedAtDesc(int userId);

    /** How many distinct game types this user has ever played. */
    @Query("SELECT COUNT(DISTINCT r.gameId) FROM GameResult r WHERE r.userId = :userId")
    int countDistinctGamesByUserId(@Param("userId") int userId);

    /** Total number of game sessions (rows) this user has ever played. */
    @Query("SELECT COUNT(r) FROM GameResult r WHERE r.userId = :userId")
    int countAllByUserId(@Param("userId") int userId);

    /** Count completed results today for a specific game (by game entity ID). */
    @Query("SELECT COUNT(r) FROM GameResult r WHERE r.userId = :userId AND r.gameId = :gameId AND r.completed = true AND r.playedAt >= :startOfDay")
    long countCompletedTodayByGameId(@Param("userId") int userId, @Param("gameId") int gameId, @Param("startOfDay") LocalDateTime startOfDay);

    /** Total game sessions played today (any game). */
    @Query("SELECT COUNT(r) FROM GameResult r WHERE r.userId = :userId AND r.playedAt >= :startOfDay")
    int countByUserIdToday(@Param("userId") int userId, @Param("startOfDay") LocalDateTime startOfDay);

    /** Sum of timePlayedSeconds for all game sessions today. */
    @Query("SELECT COALESCE(SUM(r.timePlayedSeconds), 0) FROM GameResult r WHERE r.userId = :userId AND r.playedAt >= :startOfDay")
    int sumTimePlayedSecondsByUserIdToday(@Param("userId") int userId, @Param("startOfDay") LocalDateTime startOfDay);
}
