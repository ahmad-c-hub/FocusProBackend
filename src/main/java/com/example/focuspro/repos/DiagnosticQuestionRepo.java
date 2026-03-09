package com.example.focuspro.repos;

import com.example.focuspro.entities.DiagnosticQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DiagnosticQuestionRepo extends JpaRepository<DiagnosticQuestion, Integer> {
    List<DiagnosticQuestion> findAllByOrderByDisplayOrderAsc();
}