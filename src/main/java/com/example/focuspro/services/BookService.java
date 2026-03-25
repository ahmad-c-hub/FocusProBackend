package com.example.focuspro.services;

import com.example.focuspro.dtos.BookDTO;
import com.example.focuspro.dtos.BookSnippetDTO;
import com.example.focuspro.entities.Book;
import com.example.focuspro.entities.BookSnippet;
import com.example.focuspro.entities.Users;
import com.example.focuspro.repos.BookRepo;
import com.example.focuspro.repos.BookSnippetRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookService {

    @Autowired
    private BookRepo bookRepo;

    @Autowired
    private BookSnippetRepo bookSnippetRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    // ── BOOK METHODS ──────────────────────────────────────────

    public List<BookDTO> getBooksByDifficulty(Integer level) {
        return bookRepo.findByLevel(level)
                .stream()
                .map(this::toBookDTO)
                .toList();
    }

    public BookDTO getBookByTitle(String title) {
        Book book = bookRepo.findByTitle(title)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + title));
        return toBookDTO(book);
    }

    public List<BookDTO> getAllBooks() {
        return bookRepo.findAll()
                .stream()
                .map(this::toBookDTO)
                .toList();
    }

    public BookDTO addBook(Book book) {
        Book saved = bookRepo.save(book);
        return toBookDTO(saved);
    }


    // ── SNIPPET METHODS ───────────────────────────────────────

    public List<BookSnippetDTO> getSnippetsByBookId(Integer bookId) {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        List<Integer> completedIds = jdbcTemplate.queryForList(
                "SELECT snippet_id FROM user_snippet_progress WHERE user_id = ? AND completed = true",
                Integer.class,
                user.getId()
        );

        return bookSnippetRepo.findByBookIdOrderBySequenceOrderAsc(bookId)
                .stream()
                .map(s -> toSnippetDTO(s, completedIds.contains(s.getId())))
                .toList();
    }

    public BookSnippetDTO getSnippetById(Integer snippetId) {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        BookSnippet snippet = bookSnippetRepo.findById(snippetId)
                .orElseThrow(() -> new IllegalArgumentException("Snippet not found: " + snippetId));

        boolean completed = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT completed FROM user_snippet_progress WHERE user_id = ? AND snippet_id = ?",
                Boolean.class, user.getId(), snippetId
        ));

        return toSnippetDTO(snippet, completed);
    }

    public void markSnippetAsRead(Integer snippetId) {
        Users user = (Users) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        jdbcTemplate.update(
                """
                INSERT INTO user_snippet_progress (user_id, snippet_id, completed)
                VALUES (?, ?, true)
                ON CONFLICT (user_id, snippet_id) DO UPDATE SET completed = true
                """,
                user.getId(),
                snippetId
        );
    }


    // ── MAPPERS ───────────────────────────────────────────────

    private BookDTO toBookDTO(Book book) {
        return new BookDTO(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getLevel(),
                book.getCategory(),
                book.getDescription(),
                book.getAudioUrl(),
                book.getBookPagesUrl(),
                book.getTotalPages()
        );
    }

    private BookSnippetDTO toSnippetDTO(BookSnippet snippet, boolean isCompleted) {
        return new BookSnippetDTO(
                snippet.getId(),
                snippet.getBookId(),
                snippet.getSnippetTitle(),
                snippet.getSnippetText(),
                snippet.getSnippetAudioUrl(),
                snippet.getPageNumber(),
                snippet.getDurationSeconds(),
                snippet.getSequenceOrder(),
                isCompleted
        );
    }
}