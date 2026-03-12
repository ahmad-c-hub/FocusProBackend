package com.example.focuspro.repos;

import com.example.focuspro.entities.BookSnippet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BookSnippetRepo extends JpaRepository<BookSnippet, Integer> {
    List<BookSnippet> findByBookIdOrderBySequenceOrderAsc(Integer bookId);
}