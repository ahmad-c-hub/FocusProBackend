package com.example.focuspro.repos;

import com.example.focuspro.entities.UserQuestion;
import com.example.focuspro.entities.UserQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UserQuestionRepo extends JpaRepository<UserQuestion, UserQuestionId> {

    @Transactional
    @Modifying
    @Query("DELETE FROM UserQuestion q WHERE q.userId = :userId")
    void deleteByUserId(@Param("userId") Integer userId);
}
