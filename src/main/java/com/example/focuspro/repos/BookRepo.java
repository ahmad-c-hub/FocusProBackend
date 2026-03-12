package com.example.focuspro.repos;

import com.example.focuspro.entities.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepo extends JpaRepository<Book, Integer> {
    List<Book> findByLevel(Integer level);
    Optional<Book> findByTitle(String title);
    List<Book> findByCategory(String category);
}