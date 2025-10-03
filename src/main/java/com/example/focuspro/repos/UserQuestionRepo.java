package com.example.focuspro.repos;

import com.example.focuspro.entities.UserQuestion;
import com.example.focuspro.entities.UserQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserQuestionRepo extends JpaRepository<UserQuestion, UserQuestionId> {

}
