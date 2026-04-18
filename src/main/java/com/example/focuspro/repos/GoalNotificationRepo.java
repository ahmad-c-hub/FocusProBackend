package com.example.focuspro.repos;

import com.example.focuspro.entities.GoalNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface GoalNotificationRepo extends JpaRepository<GoalNotification, Long> {

    List<GoalNotification> findByScheduledAtBeforeAndSentFalse(LocalDateTime time);

    List<GoalNotification> findByGoalIdAndSentFalse(Long goalId);

    @Transactional
    @Modifying
    @Query("DELETE FROM GoalNotification n WHERE n.goalId = :goalId AND n.sent = false")
    void deleteByGoalIdAndSentFalse(Long goalId);

    @Query("SELECT COUNT(n) FROM GoalNotification n WHERE n.goalId = :goalId AND n.notificationType = 'FOLLOWUP' AND n.sent = false")
    long countPendingFollowupsForGoal(Long goalId);
}
