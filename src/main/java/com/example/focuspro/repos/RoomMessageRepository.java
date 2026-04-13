package com.example.focuspro.repos;

import com.example.focuspro.entities.RoomMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RoomMessageRepository extends JpaRepository<RoomMessage, Long> {

    List<RoomMessage> findTop50ByRoomIdOrderBySentAtAsc(Long roomId);

    List<RoomMessage> findByRoomIdAndSentAtAfterOrderBySentAtAsc(Long roomId, Instant after);

    @Modifying
    @Query("UPDATE RoomMessage m SET m.roomId = NULL WHERE m.roomId = :roomId")
    void detachFromRoom(@Param("roomId") Long roomId);
}
