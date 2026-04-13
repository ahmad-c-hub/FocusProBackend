package com.example.focuspro.repos;

import com.example.focuspro.entities.RoomMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RoomMessageRepository extends JpaRepository<RoomMessage, Long> {

    List<RoomMessage> findTop50ByRoomIdOrderBySentAtAsc(Long roomId);

    List<RoomMessage> findByRoomIdAndSentAtAfterOrderBySentAtAsc(Long roomId, Instant after);

    void deleteByRoomId(Long roomId);
}
