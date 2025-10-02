package com.example.focuspro.repos;

import com.example.focuspro.entities.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepo extends JpaRepository<Question, Integer> {

    @Query(value = "Select * from questions q where q.level= :level order by RANDOM() limit 10", nativeQuery = true)
    List<Question> getTenRandomQuestions(@org.springframework.data.repository.query.Param("level") String level);
}
