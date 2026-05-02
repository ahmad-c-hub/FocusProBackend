package com.example.focuspro.repos;

import com.example.focuspro.entities.WebPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface WebPushSubscriptionRepo extends JpaRepository<WebPushSubscription, Long> {

    List<WebPushSubscription> findByUserId(int userId);

    Optional<WebPushSubscription> findByUserIdAndEndpoint(int userId, String endpoint);

    @Transactional
    @Modifying
    void deleteByUserIdAndEndpoint(int userId, String endpoint);

    @Transactional
    @Modifying
    @Query("DELETE FROM WebPushSubscription s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") int userId);
}
