package com.example.focuspro.repos;

import com.example.focuspro.entities.Question;
import com.example.focuspro.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepo extends JpaRepository<Question, Integer> {

    @Query(value = """
    SELECT q.* 
    FROM questions q
    WHERE q.level = :level
      AND NOT EXISTS (
          SELECT 1 
          FROM user_questions uq 
          WHERE uq.question_id = q.id
            AND uq.user_id = :userId
      )
    ORDER BY RANDOM()
    LIMIT 10
    """, nativeQuery = true)
    List<Question> getTenRandomUnansweredQuestions(@Param("level") String level, @Param("userId") int userId);

}
