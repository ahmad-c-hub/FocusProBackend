package com.example.focuspro.repos;

import com.example.focuspro.entities.CoachingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CoachingSessionRepo extends JpaRepository<CoachingSession, Long> {

    Optional<CoachingSession> findByUserIdAndSessionDateAndSessionType(
            int userId, LocalDate sessionDate, CoachingSession.SessionType sessionType);

    Optional<CoachingSession> findByUserIdAndSessionDateAndClosedAtIsNull(
            int userId, LocalDate sessionDate);

    @Transactional
    @Modifying
    @Query("DELETE FROM CoachingSession s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
