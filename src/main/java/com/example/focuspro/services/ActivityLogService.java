package com.example.focuspro.services;

import com.example.focuspro.dtos.ActivityLogDTO;
import com.example.focuspro.entities.ActivityLog;
import com.example.focuspro.repos.ActivityLogRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ActivityLogService {

    @Autowired
    private ActivityLogRepo activityLogRepo;

    public void log(int userId, String activityType, String description) {
        log(userId, activityType, description, null);
    }

    public void log(int userId, String activityType, String description, String jsonData) {
        try {
            ActivityLog entry = new ActivityLog();
            entry.setUserId(userId);
            entry.setActivityType(activityType);
            entry.setActivityDescription(description);
            entry.setActivityData(jsonData);
            entry.setActivityDate(LocalDateTime.now());
            activityLogRepo.save(entry);
        } catch (Exception e) {
            // Never let logging failures break the main flow
            System.err.println("[ActivityLog] Failed to log activity: " + e.getMessage());
        }
    }

    public List<ActivityLogDTO> getUserLogs(int userId) {
        return activityLogRepo.findByUserIdOrderByActivityDateDesc(userId)
                .stream()
                .map(l -> new ActivityLogDTO(
                        l.getId(),
                        l.getActivityType(),
                        l.getActivityDescription(),
                        l.getActivityData(),
                        l.getActivityDate()))
                .toList();
    }
}
