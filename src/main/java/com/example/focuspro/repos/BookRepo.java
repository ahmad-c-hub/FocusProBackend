package com.example.focuspro.repos;

import com.example.focuspro.dtos.BookDTO;
import com.example.focuspro.entities.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepo extends JpaRepository<Book,Integer> {

    @Query("SELECT b from Book b where b.difficulty_level=?1 ")
    List<Book> getBooksByDifficulty(String difficulty);

    @Query("SELECT b from Book b where b.title=?1")
    Optional<Book> getBookByTitle(String title);
}
