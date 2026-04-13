package com.example.focuspro.repos;

import com.example.focuspro.entities.FocusRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FocusRoomRepo extends JpaRepository<FocusRoom, Long> {

    Optional<FocusRoom> findByIdAndCreatedBy(Long id, String createdBy);

    /**
     * Rooms that have either:
     *   - never been joined  AND were created more than 2 days ago, OR
     *   - last join was more than 2 days ago
     */
    @Query("""
        SELECT r FROM FocusRoom r
        WHERE (r.lastActivityAt IS NULL AND r.createdAt < :cutoff)
           OR r.lastActivityAt < :cutoff
        """)
    List<FocusRoom> findInactiveRooms(@Param("cutoff") LocalDateTime cutoff);
}
