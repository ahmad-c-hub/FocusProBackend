package com.example.focuspro.repos;

import com.example.focuspro.entities.LockInSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LockInSessionRepo extends JpaRepository<LockInSession, Long> {
    List<LockInSession> findByUserIdAndSessionDate(int userId, LocalDate sessionDate);
    Optional<LockInSession> findByUserIdAndEndedAtIsNull(int userId);

    List<LockInSession> findByUserIdAndEndedAtIsNotNull(int userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM LockInSession s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
