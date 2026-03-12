package com.example.focuspro.controllers;

import com.example.focuspro.dtos.BookDTO;
import com.example.focuspro.dtos.BookSnippetDTO;
import com.example.focuspro.entities.Book;
import com.example.focuspro.services.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/book")
@CrossOrigin(
        origins = {
                "http://localhost:3000",
                "http://10.0.2.2:8080",
                "http://localhost:5000/"
        },
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
public class BookController {

    @Autowired
    private BookService bookService;

    // GET /book/all
    @GetMapping("/all")
    public List<BookDTO> getAllBooks() {
        return bookService.getAllBooks();
    }

    // GET /book/recommended/{level}  (level = 1, 2, 3)
    @GetMapping("/recommended/{level}")
    public List<BookDTO> getBooksByDifficulty(@PathVariable Integer level) {
        return bookService.getBooksByDifficulty(level);
    }

    // GET /book/{title}
    @GetMapping("/{title}")
    public BookDTO getBookByTitle(@PathVariable String title) {
        return bookService.getBookByTitle(title);
    }

    // POST /book/add-book  (admin only)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/add-book")
    public BookDTO addBook(@RequestBody Book book) {
        return bookService.addBook(book);
    }

    // GET /book/{bookId}/snippets
    @GetMapping("/{bookId}/snippets")
    public List<BookSnippetDTO> getSnippets(@PathVariable Integer bookId) {
        return bookService.getSnippetsByBookId(bookId);
    }

    // GET /book/snippet/{snippetId}
    @GetMapping("/snippet/{snippetId}")
    public BookSnippetDTO getSnippet(@PathVariable Integer snippetId) {
        return bookService.getSnippetById(snippetId);
    }

    // POST /book/snippet/{snippetId}/complete
    @PostMapping("/snippet/{snippetId}/complete")
    public void markSnippetAsRead(@PathVariable Integer snippetId) {
        bookService.markSnippetAsRead(snippetId);
    }
}