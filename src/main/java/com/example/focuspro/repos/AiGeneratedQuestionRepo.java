package com.example.focuspro.repos;

import com.example.focuspro.entities.AiGeneratedQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiGeneratedQuestionRepo extends JpaRepository<AiGeneratedQuestion, Long> {

    // Fetch cached questions for a specific user+snippet (avoids re-generating every time)
    List<AiGeneratedQuestion> findByUserIdAndSnippetIdAndQuestionType(
            Integer userId, Integer snippetId, String questionType);

    // Pull all retention questions generated for this user in the latest batch
    List<AiGeneratedQuestion> findByUserIdAndQuestionTypeOrderByCreatedAtDesc(
            Integer userId, String questionType);

    // How many snippets has this user already been quizzed on (for audit scheduling)
    @Query("SELECT COUNT(DISTINCT q.snippetId) FROM AiGeneratedQuestion q " +
           "WHERE q.userId = :userId AND q.questionType = 'SNIPPET'")
    long countDistinctSnippetsQuizzed(@Param("userId") Integer userId);
}
