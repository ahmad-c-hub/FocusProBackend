package com.example.focuspro.repos;

import com.example.focuspro.entities.CoachingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CoachingSessionRepo extends JpaRepository<CoachingSession, Long> {

    Optional<CoachingSession> findByUserIdAndSessionDateAndSessionType(
            int userId, LocalDate sessionDate, CoachingSession.SessionType sessionType);

    Optional<CoachingSession> findByUserIdAndSessionDateAndClosedAtIsNull(
            int userId, LocalDate sessionDate);
}
