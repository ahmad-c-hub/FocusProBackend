package com.example.focuspro.repos;

import com.example.focuspro.entities.FocusRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FocusRoomRepo extends JpaRepository<FocusRoom, Long> {
}
