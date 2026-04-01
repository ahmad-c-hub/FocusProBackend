package com.example.focuspro.repos;

import com.example.focuspro.entities.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepo extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByUserIdOrderByActivityDateDesc(int userId);
}
