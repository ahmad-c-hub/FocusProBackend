package com.example.focuspro.repos;

import com.example.focuspro.entities.LockInSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LockInSessionRepo extends JpaRepository<LockInSession, Long> {
    List<LockInSession> findByUserIdAndSessionDate(int userId, LocalDate sessionDate);
    Optional<LockInSession> findByUserIdAndEndedAtIsNull(int userId);

    /** All completed lock-in sessions for a user (endedAt is set = session finished). */
    List<LockInSession> findByUserIdAndEndedAtIsNotNull(int userId);
}
